package com.panwar2001.orgagent.features.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.chat.ChatMessage;

/**
 * One logged turn, as stored in Postgres.
 *
 * @param id message identifier
 * @param conversationId the conversation it belongs to
 * @param role who produced it
 * @param content the text
 * @param servedFromCache whether the answer came from the semantic cache
 * @param model the model that produced the answer, if any
 * @param latencyMs how long the model call took, if any
 * @param createdAt when the turn happened
 */
public record ChatMessageResponse(UUID id, UUID conversationId, String role, String content, boolean servedFromCache,
		String model, Long latencyMs, Instant createdAt) {

	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(message.getId(), message.getConversationId(), message.getRole().name(),
				message.getContent(), message.isServedFromCache(), message.getModel(), message.getLatencyMs(),
				message.getCreatedAt());
	}

}
