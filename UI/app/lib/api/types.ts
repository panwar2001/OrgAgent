/**
 * Wire types of the OrgAgent backend.
 *
 * These mirror the Java DTOs one to one; the API returns and accepts exactly these shapes.
 */

export type OrganizationStatus = "ACTIVE" | "SUSPENDED" | "ARCHIVED";
export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type DocumentStatus = "PENDING" | "INDEXED" | "FAILED";
export type ChatRole = "USER" | "ASSISTANT" | "SYSTEM";

/** Stable shape of every paged endpoint. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldViolation {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

/** The single error shape the backend produces, including from its security filter chain. */
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  code: string;
  message: string;
  path?: string;
  violations?: FieldViolation[];
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  status: OrganizationStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Project {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  description: string | null;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentDto {
  id: string;
  organizationId: string;
  projectId: string;
  title: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChatSource {
  documentId: string | null;
  title: string;
  score: number | null;
}

export interface ChatAnswer {
  conversationId: string;
  answer: string;
  fromCache: boolean;
  sources: ChatSource[];
  model: string | null;
  latencyMs: number | null;
  answeredAt: string;
}

export interface Conversation {
  id: string;
  organizationId: string;
  projectId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

/** A conversation as listed in the session rail, including the project it belongs to. */
export interface ConversationSummary {
  id: string;
  organizationId: string;
  projectId: string;
  projectName: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationWindow {
  conversationId: string;
  turns: { role: ChatRole; content: string; at: string }[];
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  role: ChatRole;
  content: string;
  servedFromCache: boolean;
  model: string | null;
  latencyMs: number | null;
  createdAt: string;
}

export interface CreateOrganizationInput {
  name: string;
  slug?: string;
}

export interface CreateProjectInput {
  name: string;
  slug?: string;
  description?: string;
}

export interface UpdateProjectInput {
  name?: string;
  description?: string;
}

export interface AskQuestionInput {
  question: string;
  conversationId?: string;
}

export interface PageQuery {
  page?: number;
  size?: number;
}
