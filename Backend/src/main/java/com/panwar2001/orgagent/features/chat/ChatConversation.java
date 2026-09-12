package com.panwar2001.orgagent.features.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * A conversation inside a project.
 *
 * <p>Redis holds the live window of a conversation; this row is what makes the window addressable
 * and what groups the permanent message log.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
public class ChatConversation {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "organization_id", nullable = false, updatable = false)
	private UUID organizationId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected ChatConversation() {
		// for JPA
	}

	private ChatConversation(UUID id, UUID organizationId, UUID projectId, String title) {
		this.id = id;
		this.organizationId = organizationId;
		this.projectId = projectId;
		this.title = title;
	}

	/** Starts a conversation, titled after the question that opened it. */
	public static ChatConversation start(UUID organizationId, UUID projectId, String firstQuestion) {
		return new ChatConversation(UUID.randomUUID(), Objects.requireNonNull(organizationId, "organizationId"),
				Objects.requireNonNull(projectId, "projectId"), titleFrom(firstQuestion));
	}

	/** Rehydrates a conversation, e.g. in tests. */
	public static ChatConversation of(UUID id, UUID organizationId, UUID projectId, String title) {
		return new ChatConversation(Objects.requireNonNull(id, "id"), organizationId, projectId, titleFrom(title));
	}

	public void retitle(String newTitle) {
		this.title = titleFrom(newTitle);
	}

	public boolean belongsTo(UUID candidateOrganizationId, UUID candidateProjectId) {
		return this.organizationId.equals(candidateOrganizationId) && this.projectId.equals(candidateProjectId);
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

	private static String titleFrom(String value) {
		String raw = value == null ? "" : value.strip().replaceAll("\\s+", " ");
		if (raw.isEmpty()) {
			return "New conversation";
		}
		return raw.length() <= 300 ? raw : raw.substring(0, 297) + "...";
	}

}
