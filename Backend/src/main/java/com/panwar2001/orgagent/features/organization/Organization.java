package com.panwar2001.orgagent.features.organization;

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
 * An organization account: the tenant that owns projects, documents and conversations.
 *
 * <p>The identifier is generated in the application rather than read back from the database, so a
 * freshly created organization is complete before it is persisted and tests never need a database to
 * know an id.
 */
@Entity
@Table(name = "organizations")
@Getter
public class Organization {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "slug", nullable = false, length = 120)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private OrganizationStatus status = OrganizationStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Optimistic locking: two admins renaming the same account at once must not silently win. */
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Organization() {
		// for JPA
	}

	private Organization(UUID id, String name, String slug) {
		this.id = id;
		this.name = name;
		this.slug = slug;
	}

	/** Creates a new active organization. */
	public static Organization create(String name, String slug) {
		return new Organization(UUID.randomUUID(), requireText(name, "name"), requireText(slug, "slug"));
	}

	/** Rehydrates an organization, e.g. in tests or when loading from an external source. */
	public static Organization of(UUID id, String name, String slug, OrganizationStatus status) {
		Organization organization = new Organization(Objects.requireNonNull(id, "id"),
				requireText(name, "name"), requireText(slug, "slug"));
		organization.status = Objects.requireNonNull(status, "status");
		return organization;
	}

	public void rename(String newName) {
		this.name = requireText(newName, "name");
	}

	public void changeSlug(String newSlug) {
		this.slug = requireText(newSlug, "slug");
	}

	public void suspend() {
		this.status = OrganizationStatus.SUSPENDED;
	}

	public void activate() {
		this.status = OrganizationStatus.ACTIVE;
	}

	public void archive() {
		this.status = OrganizationStatus.ARCHIVED;
	}

	public boolean isActive() {
		return this.status == OrganizationStatus.ACTIVE;
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
			throw new IllegalArgumentException("Organization " + field + " must not be blank");
		}
		return value.trim();
	}

}
