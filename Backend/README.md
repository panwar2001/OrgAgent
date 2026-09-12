# OrgAgent Backend

A multi-tenant RAG chatbot over an organization's internal documents.

Each **organization account** owns **projects**. Each project accepts **document ingestion** and
answers **questions** grounded in the documents that were ingested into it — never in another
project's, and never in another tenant's.

- Java 25, Spring Boot 4, virtual threads everywhere
- PostgreSQL 18 + pgvector for retrieval, Redis 8 for the live conversation window
- Flyway owns the schema; Hibernate only validates against it
- Google GenAI (Gemini) for chat and embeddings, behind a narrow interface so it can be swapped

---

## Architecture

Feature-first packages: everything a feature needs sits together, and features depend on `core`,
never on each other's internals.

```
com.panwar2001.orgagent
├── Application.java
├── core
│   ├── config      OrgAgentProperties, CorsConfig, SwaggerConfiguration, FlywayConfig,
│                   AsyncConfig, VectorStoreConfig
│   ├── exception   ErrorCode, ApiError, ApiException hierarchy, GlobalRestExceptionHandler
│   ├── redis       RedisConfig, RedisKeys, ChatWindowStore, ChatTurn/ChatRole
│   ├── security    SecurityConfig, ApiErrorWriter, ApiErrorAuthenticationEntryPoint,
│                   ApiAccessDeniedHandler
│   ├── support     Slugifier
│   └── web         PageResponse, Pagination
└── features
    ├── organization  accounts (tenant root)
    ├── project       projects inside an account, plus their documents' namespace
    ├── ingestion     Document entity, extractors, TextChunker, IngestionService, DocumentController
    └── chat          conversations, message log, RAG retrieval, LLM call, semantic cache,
                      ChatService, ChatPersistenceService, ChatController
```

### One chat turn

```
POST /chat
   │
   ├─ 1. load project (404/409 if missing or archived)
   ├─ 2. Redis: last N turns of the conversation            ← live window, not the database
   ├─ 3. pgvector semantic cache lookup by question similarity
   │        hit  ─────────────────────────────────────────► answer, fromCache = true
   └─ 4. miss
          ├─ pgvector similarity search, filtered by projectId
          ├─ prompt = instructions + numbered context + window + question
          ├─ ChatModel.call(prompt)
          └─ 5. answer returned to the user
                 ├─ @Async Redis   append question + answer to the window
                 ├─ @Async Postgres append both turns to chat_messages (the integration log)
                 └─ @Async pgvector store the question/answer pair in the semantic cache
```

Step 5 never delays the response: it runs on virtual threads, and each write swallows its own
failure — a Redis hiccup costs context, not the answer, and Postgres stays the system of record.

### Why the live window lives in Redis

The window is read on every single turn and written on every single turn. In Redis it is one
`LRANGE` on a list that is `LPUSH`ed and `LTRIM`ed down to `chat-window-size` entries with a TTL;
in Postgres it would be an indexed scan of a table that only ever grows. The permanent log still
goes to Postgres, so nothing is lost when the window expires.

### Data model

| Table | Purpose |
| --- | --- |
| `organizations` | tenant accounts, unique slug, lifecycle status |
| `projects` | per-tenant projects, slug unique within the organization, cascade delete |
| `documents` | ingested files with status (`PENDING`/`INDEXED`/`FAILED`), chunk count, failure reason |
| `document_embeddings` | pgvector table for embedded chunks (Spring AI layout, created by Flyway) |
| `chat_conversations` | conversations, titled after their first question |
| `chat_messages` | append-only integration log: role, content, model, latency, cache flag |
| `chat_semantic_cache` | pgvector table of question/answer pairs with per-entry expiry |

Migrations: `V1__organizations_and_projects.sql`, `V2__documents_and_embeddings.sql`,
`V3__chat.sql`.

---

## API

All endpoints are under `/api/v1` and are **currently unauthenticated by decision**; the security
chain, JSON 401/403 responses and the bearer scheme in the OpenAPI document are already in place so
authentication is a rule change, not a rewrite. Swagger UI: `/swagger-ui.html`.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/organizations` | create an account |
| `GET` | `/organizations` | list accounts (paged) |
| `GET` `PATCH` `DELETE` | `/organizations/{id}` | read / rename / delete |
| `POST` | `/organizations/{id}/suspend` \| `/activate` | lifecycle |
| `POST` `GET` | `/organizations/{orgId}/projects` | create / list projects |
| `GET` `PATCH` `DELETE` | `/organizations/{orgId}/projects/{id}` | read / update / delete |
| `POST` | `/organizations/{orgId}/projects/{id}/archive` \| `/activate` | lifecycle |
| `POST` | `/organizations/{orgId}/projects/{id}/documents` | upload a file (multipart `file`) and embed it |
| `GET` | `/organizations/{orgId}/projects/{id}/documents` | list documents with ingestion status |
| `GET` `DELETE` | `/organizations/{orgId}/projects/{id}/documents/{documentId}` | read / delete (vectors too) |
| `POST` | `/organizations/{orgId}/projects/{id}/chat` | ask a question |
| `GET` | `/organizations/{orgId}/projects/{id}/chat` | list conversations |
| `GET` | `/organizations/{orgId}/projects/{id}/chat/{conversationId}` | live window, served from Redis |
| `GET` | `/organizations/{orgId}/projects/{id}/chat/{conversationId}/history` | permanent log from Postgres |
| `DELETE` | `/organizations/{orgId}/projects/{id}/chat/{conversationId}` | drop conversation, window and log |

Ask:

```bash
curl -s localhost:8080/api/v1/organizations/$ORG/projects/$PROJECT/chat \
  -H 'content-type: application/json' \
  -d '{"question":"How long do refunds take?"}'
```

```json
{
  "conversationId": "…",
  "answer": "Refunds are processed within five working days. (Refund policy)",
  "fromCache": false,
  "sources": [{ "documentId": "…", "title": "Refund policy", "score": 0.91 }],
  "model": "gemini-2.5-flash",
  "latencyMs": 812,
  "answeredAt": "2026-01-01T00:00:00Z"
}
```

Pass `conversationId` back in to continue the conversation.

### Errors

Every failure — including ones raised inside the security filter chain — is:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 404,
  "code": "PROJECT_NOT_FOUND",
  "message": "Project not found: …",
  "path": "/api/v1/organizations/…/projects/…",
  "violations": [{ "field": "name", "message": "must not be blank", "rejectedValue": "" }]
}
```

`code` is a stable enum name (`ErrorCode`) — branch on that, never on `message`.

---

## Running it

```bash
cp .env.example .env          # set GEMINI_API_KEY
docker compose up --build     # postgres + pgvector, redis, backend
```

The database image is `postgres:18-alpine` plus the pgvector extension (installed from Alpine's
`postgresql-pgvector` package in `infra/postgres/Dockerfile`), because the plain image does not ship
it. Flyway migrates on startup.

Without containers:

```bash
export GEMINI_API_KEY=…
./gradlew bootRun
```

`POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `SERVER_PORT` and
`LOG_LEVEL` are read from the environment; see `src/main/resources/application.yaml` for the rest.

### Configuration worth knowing

| Property | Meaning |
| --- | --- |
| `orgagent.rag.chat-window-size` | how many recent turns Redis replays into the prompt (default 20) |
| `orgagent.rag.chat-window-ttl` | how long an idle conversation's window survives |
| `orgagent.rag.retrieval-top-k` | passages pulled from pgvector per question |
| `orgagent.rag.retrieval-similarity-threshold` | minimum cosine similarity for a passage to be used |
| `orgagent.rag.embedding-dimensions` | vector width; must match the embedding model and the migrations |
| `orgagent.cache.semantic.*` | semantic answer cache: on/off, hit threshold, TTL |
| `orgagent.ingestion.*` | upload size limit, chunk size and overlap |

---

## Tests

```bash
./gradlew test
```

Fast, infrastructure-free and deterministic: repositories, Redis, the vector stores, the chat model
and the model provider are all mocked or replaced, so the suite runs without Docker.

What is covered:

- controllers through standalone `MockMvc` with real bean validation and the real
  `GlobalRestExceptionHandler` — status codes, error codes, field violations
- the security filter chain, built in a servlet context, asserting authorization decisions and the
  JSON 401/403 bodies
- service logic: tenancy scoping, conflict and not-found paths, slug derivation, ingestion failure
  handling, the cache-hit and cache-miss chats, prompt contents and async side effects
- the vector store wiring (two stores of the same type, injected by qualifier)
- migration hygiene: naming, unique versions, and the tables/columns each migration must create

Not covered here: real Postgres/pgvector and Redis round trips, and calls to the model provider.
Those need containers and a provider key and are the natural next layer (`@SpringBootTest` with
Testcontainers).

---

## Decisions and additions

Beyond the libraries the project already had, these were added and are used deliberately:

| Library | Why |
| --- | --- |
| `spring-boot-starter-flyway`, `flyway-database-postgresql` | requested: Flyway owns the schema |
| `spring-boot-starter-security` | requested: `core/security` filter chain (endpoints still open) |
| `springdoc-openapi-starter-webmvc-ui` | requested: `SwaggerConfiguration`, browsable API |
| `spring-boot-starter-test`, `spring-security-test` | JUnit 5, Mockito, AssertJ, MockMvc security |

Decisions worth flagging:

- **No JWT library yet.** Authentication is deliberately deferred; the chain is stateless, CSRF is
  off, CORS is answered before authorization, and everything except the documented endpoints is
  denied by default. Turning auth on means replacing the `/api/**` rule.
- **Chunking is ours, not `TokenTextSplitter`.** Spring AI's splitter cannot overlap chunks, and
  overlap is what keeps a sentence that straddles a boundary retrievable. `TextChunker` is
  paragraph-aware, hard-cuts oversized paragraphs and never exceeds the configured size.
- **`PgVectorStore` is configured in code, not properties.** The service needs two stores (documents
  and the answer cache); Spring AI's auto-configuration creates one.
- **The semantic cache is a second vector store, not a Redis structure.** It reuses the same
  embedding model, gets the same cosine-similarity semantics as retrieval, and does not need a
  separate search engine.
- **Documents are committed before embedding.** A crash mid-ingestion leaves a `FAILED` row with the
  reason instead of an invisible gap between "uploaded" and "searchable".
- **Spring Boot is pinned to a released version (4.1.1), not a snapshot.** `spring init` had selected
  `4.1.2-SNAPSHOT` from `repo.spring.io/snapshot`; snapshot metadata churns, so the IDE intermittently
  reports Spring artifacts and the Boot plugin as unresolved until it re-resolves them. Every
  dependency this build needs is a released artifact on Maven Central, so no snapshot repository is
  declared anywhere. To move back to the snapshot, change the plugin version and re-add
  `maven { url = uri("https://repo.spring.io/snapshot") }` to both `pluginManagement` in
  `settings.gradle.kts` and `repositories` in `build.gradle.kts`.
- **Third-party versions live in plain script values** (`springAiVersion`, `springdocVersion`) rather
  than `extra` + `property(...)`, so an IDE reading the script resolves them without a property lookup.

## Possible next steps

1. Authentication (JWT or OIDC) plus per-organization roles — the chain is ready for it.
2. Streaming answers (SSE) — `ChatModel.stream` behind the existing `AnswerGenerator` interface.
3. Re-ingestion and re-embedding when the embedding model or chunking changes.
4. Testcontainers integration tests for real pgvector and Redis behaviour.
