# Postgres 18 (Alpine) + the pgvector extension, which RAG retrieval needs.
# Alpine ships pgvector in community as `postgresql-pgvector`, built against
# postgresql18 on Alpine >= 3.23, which is what postgres:18-alpine is based on.
FROM postgres:18-alpine

RUN apk add --no-cache postgresql-pgvector

COPY init-extensions.sql /docker-entrypoint-initdb.d/10-init-extensions.sql
