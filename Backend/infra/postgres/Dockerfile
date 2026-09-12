# Postgres 18 (Alpine) + the pgvector extension, which RAG retrieval needs.
#
# Alpine ships pgvector in community as `postgresql-pgvector`, built against postgresql18.
# It installs into the layout of Alpine's own postgresql18 package (/usr/share/postgresql18,
# /usr/lib/postgresql18), whereas the postgres:18-alpine image compiles PostgreSQL into
# /usr/local. The server will not find the extension unless it is copied into the directories
# it actually searches, which pg_config reports.
FROM postgres:18-alpine

RUN apk add --no-cache postgresql-pgvector \
 && cp /usr/share/postgresql18/extension/vector* "$(pg_config --sharedir)/extension/" \
 && cp /usr/lib/postgresql18/vector.so "$(pg_config --pkglibdir)/" \
 && chmod 0644 "$(pg_config --sharedir)/extension/"vector* "$(pg_config --pkglibdir)/vector.so"

COPY init-extensions.sql /docker-entrypoint-initdb.d/10-init-extensions.sql

# The entrypoint runs the init scripts as the unprivileged `postgres` user, so they must be
# world-readable whatever umask the machine that built the image happened to have.
RUN chmod 0644 /docker-entrypoint-initdb.d/10-init-extensions.sql
