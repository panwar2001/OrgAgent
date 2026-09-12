-- Ingested documents and the pgvector table the retrieval step searches.
-- The vector table is created here rather than by Spring AI so Flyway stays the single
-- source of truth for the schema (spring.ai.vectorstore.pgvector.initialize-schema=false).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    project_id       uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title            varchar(300) NOT NULL,
    file_name        varchar(300) NOT NULL,
    content_type     varchar(150) NOT NULL,
    size_bytes       bigint       NOT NULL,
    content_hash     varchar(64)  NOT NULL,
    status           varchar(32)  NOT NULL DEFAULT 'PENDING',
    chunk_count      integer      NOT NULL DEFAULT 0,
    error_message    text,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_documents_project_content_hash UNIQUE (project_id, content_hash)
);

CREATE INDEX idx_documents_project_id ON documents (project_id);
CREATE INDEX idx_documents_organization_id ON documents (organization_id);
CREATE INDEX idx_documents_status ON documents (status);

-- Layout expected by Spring AI's PgVectorStore: id, content, metadata (json), embedding.
CREATE TABLE document_embeddings (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content    text,
    metadata   json,
    embedding  vector(768)
);

CREATE INDEX idx_document_embeddings_hnsw ON document_embeddings USING hnsw (embedding vector_cosine_ops);
