/**
 * Minimal stand-in for the OrgAgent Spring Boot API.
 *
 * It exists so the UI can be developed and demoed without Postgres, Redis or a Gemini key.
 * Every response matches the real DTOs, so switching to the real backend is a matter of
 * pointing API_BASE_URL back at it.
 *
 *   node scripts/mock-backend.mjs           # listens on 127.0.0.1:8080
 */
import { createServer } from "node:http";

const PORT = Number(process.env.PORT ?? 8080);

const ORG_ID = "11111111-1111-1111-1111-111111111111";
const PROJECT_ID = "22222222-2222-2222-2222-222222222222";
const CONVERSATION_ID = "33333333-3333-3333-3333-333333333333";
const DOCUMENT_ID = "44444444-4444-4444-4444-444444444444";

const now = () => new Date().toISOString();

const organization = {
  id: ORG_ID,
  name: "Acme Ltd.",
  slug: "acme-ltd",
  status: "ACTIVE",
  createdAt: now(),
  updatedAt: now(),
};

const project = {
  id: PROJECT_ID,
  organizationId: ORG_ID,
  name: "HR Policies",
  slug: "hr-policies",
  description: "Everything employees ask about HR.",
  status: "ACTIVE",
  createdAt: now(),
  updatedAt: now(),
};

const document = {
  id: DOCUMENT_ID,
  organizationId: ORG_ID,
  projectId: PROJECT_ID,
  title: "refund-policy",
  fileName: "refund-policy.md",
  contentType: "text/markdown",
  sizeBytes: 4096,
  status: "INDEXED",
  chunkCount: 7,
  errorMessage: null,
  createdAt: now(),
  updatedAt: now(),
};

const conversation = {
  id: CONVERSATION_ID,
  organizationId: ORG_ID,
  projectId: PROJECT_ID,
  title: "How long do refunds take?",
  createdAt: now(),
  updatedAt: now(),
};

const messages = [
  {
    id: "55555555-5555-5555-5555-555555555551",
    conversationId: CONVERSATION_ID,
    role: "USER",
    content: "How long do refunds take?",
    servedFromCache: false,
    model: null,
    latencyMs: null,
    createdAt: now(),
  },
  {
    id: "55555555-5555-5555-5555-555555555552",
    conversationId: CONVERSATION_ID,
    role: "ASSISTANT",
    content: "Refunds are processed within five working days (refund-policy).",
    servedFromCache: false,
    model: "gemini-2.5-flash",
    latencyMs: 812,
    createdAt: now(),
  },
];

const page = (content, size = 20) => ({
  content,
  page: 0,
  size,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
});

const routes = [
  [/^\/api\/v1\/organizations$/, () => page([organization])],
  [new RegExp(`^/api/v1/organizations/${ORG_ID}$`), () => organization],
  [new RegExp(`^/api/v1/organizations/${ORG_ID}/projects$`), () => page([project])],
  [new RegExp(`^/api/v1/organizations/${ORG_ID}/projects/${PROJECT_ID}$`), () => project],
  [new RegExp(`^/api/v1/organizations/${ORG_ID}/projects/${PROJECT_ID}/documents$`), () => page([document])],
  [new RegExp(`^/api/v1/organizations/${ORG_ID}/projects/${PROJECT_ID}/chat$`), () => page([conversation])],
  [
    new RegExp(`^/api/v1/organizations/${ORG_ID}/projects/${PROJECT_ID}/chat/${CONVERSATION_ID}$`),
    () => ({
      conversationId: CONVERSATION_ID,
      turns: messages.map((message) => ({
        role: message.role,
        content: message.content,
        at: message.createdAt,
      })),
    }),
  ],
  [
    new RegExp(`^/api/v1/organizations/${ORG_ID}/projects/${PROJECT_ID}/chat/${CONVERSATION_ID}/history$`),
    () => page(messages),
  ],
];

const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host}`);
  const send = (status, body) => {
    response.writeHead(status, { "content-type": "application/json" });
    response.end(JSON.stringify(body));
  };

  if (request.method === "POST" && url.pathname.endsWith("/chat")) {
    let raw = "";
    request.on("data", (chunk) => (raw += chunk));
    request.on("end", () => {
      const question = JSON.parse(raw || "{}").question ?? "question";
      send(200, {
        conversationId: CONVERSATION_ID,
        answer: `Mock answer for: ${question}`,
        fromCache: false,
        sources: [{ documentId: DOCUMENT_ID, title: "refund-policy", score: 0.91 }],
        model: "gemini-2.5-flash",
        latencyMs: 42,
        answeredAt: now(),
      });
    });
    return;
  }

  for (const [pattern, handler] of routes) {
    if (pattern.test(url.pathname)) {
      send(200, handler());
      return;
    }
  }

  send(404, { status: 404, code: "RESOURCE_NOT_FOUND", message: `No mock for ${url.pathname}`, path: url.pathname });
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`mock backend listening on http://127.0.0.1:${PORT}`);
});
