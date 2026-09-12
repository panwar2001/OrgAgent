import type {
  ApiErrorBody,
  AskQuestionInput,
  ChatAnswer,
  ChatMessage,
  Conversation,
  ConversationSummary,
  ConversationWindow,
  CreateOrganizationInput,
  CreateProjectInput,
  DocumentDto,
  FieldViolation,
  Organization,
  Page,
  PageQuery,
  Project,
  UpdateProjectInput,
} from "./types";

/**
 * A failure the UI can render: either the backend rejected the request (it sends a code and,
 * for validation failures, per-field violations) or it could not be reached at all.
 */
export class ApiError extends Error {
  readonly status: number;

  readonly code: string;

  readonly violations: FieldViolation[];

  readonly path?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    violations: FieldViolation[] = [],
    path?: string,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.violations = violations;
    this.path = path;
  }

  /** True when the request never reached the backend. */
  get isUnreachable(): boolean {
    return this.code === "BACKEND_UNREACHABLE";
  }

  toBody(): ApiErrorBody {
    return {
      status: this.status,
      code: this.code,
      message: this.message,
      path: this.path,
      violations: this.violations,
    };
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/**
 * Thin, typed HTTP client for the Spring Boot backend.
 *
 * Every call goes through {@link request}, so error translation (including "the backend is not
 * running") happens in exactly one place.
 */
export interface ApiClient {
  listOrganizations(query?: PageQuery): Promise<Page<Organization>>;
  getOrganization(organizationId: string): Promise<Organization>;
  createOrganization(input: CreateOrganizationInput): Promise<Organization>;
  renameOrganization(organizationId: string, name: string): Promise<Organization>;
  suspendOrganization(organizationId: string): Promise<Organization>;
  activateOrganization(organizationId: string): Promise<Organization>;
  deleteOrganization(organizationId: string): Promise<void>;

  listProjects(organizationId: string, query?: PageQuery): Promise<Page<Project>>;
  getProject(organizationId: string, projectId: string): Promise<Project>;
  createProject(organizationId: string, input: CreateProjectInput): Promise<Project>;
  updateProject(organizationId: string, projectId: string, input: UpdateProjectInput): Promise<Project>;
  archiveProject(organizationId: string, projectId: string): Promise<Project>;
  activateProject(organizationId: string, projectId: string): Promise<Project>;
  deleteProject(organizationId: string, projectId: string): Promise<void>;

  listDocuments(organizationId: string, projectId: string, query?: PageQuery): Promise<Page<DocumentDto>>;
  uploadDocument(organizationId: string, projectId: string, file: File): Promise<DocumentDto>;
  deleteDocument(organizationId: string, projectId: string, documentId: string): Promise<void>;

  ask(organizationId: string, projectId: string, input: AskQuestionInput): Promise<ChatAnswer>;
  listConversations(organizationId: string, projectId: string, query?: PageQuery): Promise<Page<Conversation>>;
  listOrganizationConversations(
    organizationId: string,
    query?: PageQuery,
  ): Promise<Page<ConversationSummary>>;
  getConversationWindow(
    organizationId: string,
    projectId: string,
    conversationId: string,
  ): Promise<ConversationWindow>;
  getConversationHistory(
    organizationId: string,
    projectId: string,
    conversationId: string,
    query?: PageQuery,
  ): Promise<Page<ChatMessage>>;
  deleteConversation(organizationId: string, projectId: string, conversationId: string): Promise<void>;
}

export function createApiClient(baseUrl: string): ApiClient {
  const root = baseUrl.replace(/\/+$/, "");

  /** Adds pagination to a path. The base URL is added once, by request(). */
  function withQuery(path: string, query?: PageQuery): string {
    const search = new URLSearchParams();
    if (query?.page !== undefined) search.set("page", String(query.page));
    if (query?.size !== undefined) search.set("size", String(query.size));
    return search.size > 0 ? `${path}?${search}` : path;
  }

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${root}${path}`, {
        ...init,
        headers: {
          accept: "application/json",
          // FormData sets its own multipart boundary.
          ...(init?.body instanceof FormData ? {} : { "content-type": "application/json" }),
          ...init?.headers,
        },
      });
    } catch (cause) {
      // The browser sees a friendly message; the Worker log keeps the real reason.
      console.error(`[orgagent] ${init?.method ?? "GET"} ${root}${path} failed`, cause);
      throw new ApiError(
        503,
        "BACKEND_UNREACHABLE",
        `Cannot reach the OrgAgent backend at ${root}. Start it (docker compose up) or point API_BASE_URL at a running instance.`,
        [],
        path,
      );
    }

    if (response.status === 204) {
      return undefined as T;
    }

    const text = await response.text();
    let payload: unknown;
    try {
      payload = text ? JSON.parse(text) : undefined;
    } catch {
      payload = undefined;
    }

    if (!response.ok) {
      const body = (payload ?? {}) as Partial<ApiErrorBody>;
      throw new ApiError(
        response.status,
        body.code ?? `HTTP_${response.status}`,
        body.message ?? response.statusText ?? "Request failed",
        body.violations ?? [],
        body.path ?? path,
      );
    }

    return payload as T;
  }

  const base = (organizationId: string, projectId: string) =>
    `/api/v1/organizations/${organizationId}/projects/${projectId}`;

  return {
    listOrganizations: (query) => request(withQuery("/api/v1/organizations", query)),
    getOrganization: (organizationId) => request(`/api/v1/organizations/${organizationId}`),
    createOrganization: (input) =>
      request("/api/v1/organizations", { method: "POST", body: JSON.stringify(input) }),
    renameOrganization: (organizationId, name) =>
      request(`/api/v1/organizations/${organizationId}`, {
        method: "PATCH",
        body: JSON.stringify({ name }),
      }),
    suspendOrganization: (organizationId) =>
      request(`/api/v1/organizations/${organizationId}/suspend`, { method: "POST" }),
    activateOrganization: (organizationId) =>
      request(`/api/v1/organizations/${organizationId}/activate`, { method: "POST" }),
    deleteOrganization: (organizationId) =>
      request(`/api/v1/organizations/${organizationId}`, { method: "DELETE" }),

    listProjects: (organizationId, query) => request(withQuery(`/api/v1/organizations/${organizationId}/projects`, query)),
    getProject: (organizationId, projectId) => request(base(organizationId, projectId)),
    createProject: (organizationId, input) =>
      request(`/api/v1/organizations/${organizationId}/projects`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    updateProject: (organizationId, projectId, input) =>
      request(base(organizationId, projectId), { method: "PATCH", body: JSON.stringify(input) }),
    archiveProject: (organizationId, projectId) =>
      request(`${base(organizationId, projectId)}/archive`, { method: "POST" }),
    activateProject: (organizationId, projectId) =>
      request(`${base(organizationId, projectId)}/activate`, { method: "POST" }),
    deleteProject: (organizationId, projectId) =>
      request(base(organizationId, projectId), { method: "DELETE" }),

    listDocuments: (organizationId, projectId, query) =>
      request(withQuery(`${base(organizationId, projectId)}/documents`, query)),
    uploadDocument: (organizationId, projectId, file) => {
      const form = new FormData();
      form.append("file", file);
      return request(`${base(organizationId, projectId)}/documents`, { method: "POST", body: form });
    },
    deleteDocument: (organizationId, projectId, documentId) =>
      request(`${base(organizationId, projectId)}/documents/${documentId}`, { method: "DELETE" }),

    ask: (organizationId, projectId, input) =>
      request(`${base(organizationId, projectId)}/chat`, { method: "POST", body: JSON.stringify(input) }),
    listConversations: (organizationId, projectId, query) =>
      request(withQuery(`${base(organizationId, projectId)}/chat`, query)),
    listOrganizationConversations: (organizationId, query) =>
      request(withQuery(`/api/v1/organizations/${organizationId}/conversations`, query)),
    getConversationWindow: (organizationId, projectId, conversationId) =>
      request(`${base(organizationId, projectId)}/chat/${conversationId}`),
    getConversationHistory: (organizationId, projectId, conversationId, query) =>
      request(withQuery(`${base(organizationId, projectId)}/chat/${conversationId}/history`, query)),
    deleteConversation: (organizationId, projectId, conversationId) =>
      request(`${base(organizationId, projectId)}/chat/${conversationId}`, { method: "DELETE" }),
  };
}
