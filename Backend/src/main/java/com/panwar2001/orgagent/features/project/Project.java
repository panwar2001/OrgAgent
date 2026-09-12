package com.panwar2001.orgagent.features.project;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * A project inside an organization: the unit that documents are ingested into and questions are
 * asked against, so one chatbot never mixes two bodies of knowledge.
 *
 * <p>The organization is referenced by id rather than by a JPA association: every query in this
 * service is already scoped by organization id, and an id makes that scoping explicit and cheap.
 */
@Entity
@Table(name = "projects")
@Getter
public class Project {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "organization_id", nullable = false, updatable = false)
	private UUID organizationId;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "slug", nullable = false, length = 120)
	private String slug;

	@Column(name = "description")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private ProjectStatus status = ProjectStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Project() {
		// for JPA
	}

	private Project(UUID id, UUID organizationId, String name, String slug, String description) {
		this.id = id;
		this.organizationId = organizationId;
		this.name = name;
		this.slug = slug;
		this.description = description;
	}

	public static Project create(UUID organizationId, String name, String slug, String description) {
		return new Project(UUID.randomUUID(), Objects.requireNonNull(organizationId, "organizationId"),
				requireText(name, "name"), requireText(slug, "slug"), normalize(description));
	}

	/** Rehydrates a project, e.g. in tests. */
	public static Project of(UUID id, UUID organizationId, String name, String slug, String description,
			ProjectStatus status) {
		Project project = new Project(Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(organizationId, "organizationId"), requireText(name, "name"),
				requireText(slug, "slug"), normalize(description));
		project.status = Objects.requireNonNull(status, "status");
		return project;
	}

	public void rename(String newName) {
		this.name = requireText(newName, "name");
	}

	public void describe(String newDescription) {
		this.description = normalize(newDescription);
	}

	public void archive() {
		this.status = ProjectStatus.ARCHIVED;
	}

	public void activate() {
		this.status = ProjectStatus.ACTIVE;
	}

	public boolean isActive() {
		return this.status == ProjectStatus.ACTIVE;
	}

	/** True when this project belongs to the given organization; the tenancy check. */
	public boolean belongsTo(UUID candidateOrganizationId) {
		return this.organizationId.equals(candidateOrganizationId);
	}

	@PrePersist
	void onInsert() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Project " + field + " must not be blank");
		}
		return value.trim();
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
