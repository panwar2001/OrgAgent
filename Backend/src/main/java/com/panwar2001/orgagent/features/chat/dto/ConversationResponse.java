package com.panwar2001.orgagent.features.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.chat.ChatConversation;

/**
 * A conversation as returned by the API.
 *
 * @param id conversation identifier
 * @param organizationId owning organization
 * @param projectId the project it is about
 * @param title title derived from the first question
 * @param createdAt when it started
 * @param updatedAt when it last received a turn
 */
public record ConversationResponse(UUID id, UUID organizationId, UUID projectId, String title, Instant createdAt,
		Instant updatedAt) {

	public static ConversationResponse from(ChatConversation conversation) {
		return new ConversationResponse(conversation.getId(), conversation.getOrganizationId(),
				conversation.getProjectId(), conversation.getTitle(), conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}

}
