package com.panwar2001.orgagent.features.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.panwar2001.orgagent.core.redis.ChatRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One turn of a conversation as permanently logged in Postgres.
 *
 * <p>This is the integration log: it is append-only, keeps the model that produced an answer and
 * whether it came from the semantic cache, and is what audits and analytics read. The live prompt
 * history is read from Redis instead.
 */
@Entity
@Table(name = "chat_messages")
@Getter
public class ChatMessage {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Column(name = "organization_id", nullable = false, updatable = false)
	private UUID organizationId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 32)
	private ChatRole role;

	@Column(name = "content", nullable = false)
	private String content;

	@Column(name = "served_from_cache", nullable = false)
	private boolean servedFromCache;

	@Column(name = "model", length = 120)
	private String model;

	@Column(name = "latency_ms")
	private Long latencyMs;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatMessage() {
		// for JPA
	}

	private ChatMessage(UUID id, UUID conversationId, UUID organizationId, UUID projectId, ChatRole role,
			String content, boolean servedFromCache, String model, Long latencyMs, Instant createdAt) {
		this.id = id;
		this.conversationId = conversationId;
		this.organizationId = organizationId;
		this.projectId = projectId;
		this.role = role;
		this.content = content;
		this.servedFromCache = servedFromCache;
		this.model = model;
		this.latencyMs = latencyMs;
		this.createdAt = createdAt;
	}

	/** Logs a question asked by the end user. */
	public static ChatMessage question(UUID conversationId, UUID organizationId, UUID projectId, String content,
			Instant at) {
		return new ChatMessage(UUID.randomUUID(), conversationId, organizationId, projectId, ChatRole.USER, content,
				false, null, null, at);
	}

	/** Logs an answer produced for the user. */
	public static ChatMessage answer(UUID conversationId, UUID organizationId, UUID projectId, String content,
			boolean servedFromCache, String model, Long latencyMs, Instant at) {
		return new ChatMessage(UUID.randomUUID(), conversationId, organizationId, projectId, ChatRole.ASSISTANT,
				content, servedFromCache, model, latencyMs, at);
	}

	/** Rehydrates a message, e.g. in tests. */
	public static ChatMessage of(UUID id, UUID conversationId, ChatRole role, String content, Instant at) {
		return new ChatMessage(id, conversationId, UUID.randomUUID(), UUID.randomUUID(), role,
				Objects.requireNonNull(content, "content"), false, null, null, at);
	}

}
