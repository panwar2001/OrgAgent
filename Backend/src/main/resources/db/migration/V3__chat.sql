-- Conversations, the Postgres integration log of every turn, and the semantic answer cache.

CREATE TABLE chat_conversations (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    project_id       uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title            varchar(300) NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_conversations_project ON chat_conversations (organization_id, project_id);

-- The system of record for chats. Redis holds only a rolling window of the live conversation;
-- everything that was ever said ends up here.
CREATE TABLE chat_messages (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  uuid         NOT NULL REFERENCES chat_conversations (id) ON DELETE CASCADE,
    organization_id  uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    role             varchar(32)  NOT NULL,
    content          text         NOT NULL,
    served_from_cache boolean     NOT NULL DEFAULT false,
    model            varchar(120),
    latency_ms       bigint,
    created_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, created_at);
CREATE INDEX idx_chat_messages_project ON chat_messages (project_id, created_at DESC);

-- Semantic answer cache. Same physical layout Spring AI's PgVectorStore expects, but its own
-- table, so a question similar to an already answered one never reaches the model provider.
CREATE TABLE chat_semantic_cache (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content    text,
    metadata   json,
    embedding  vector(768)
);

CREATE INDEX idx_chat_semantic_cache_hnsw ON chat_semantic_cache USING hnsw (embedding vector_cosine_ops);
