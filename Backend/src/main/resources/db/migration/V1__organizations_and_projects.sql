-- Organizations and projects: the tenancy backbone of the service.
-- Flyway owns the schema; Hibernate only validates against it.

CREATE TABLE organizations (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(200) NOT NULL,
    slug        varchar(120) NOT NULL,
    status      varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

CREATE INDEX idx_organizations_status ON organizations (status);

CREATE TABLE projects (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             varchar(200) NOT NULL,
    slug             varchar(120) NOT NULL,
    description      text,
    status           varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_projects_organization_slug UNIQUE (organization_id, slug)
);

CREATE INDEX idx_projects_organization_id ON projects (organization_id);
CREATE INDEX idx_projects_status ON projects (status);
