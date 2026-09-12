package com.panwar2001.orgagent.features.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.chat.ChatConversation;

/**
 * A conversation as listed in the console's session rail: enough to label and open it, across every
 * project of an organization.
 *
 * @param id conversation identifier
 * @param organizationId owning organization
 * @param projectId project the conversation belongs to
 * @param projectName name of that project, so sessions from different projects are distinguishable
 * @param title title derived from the first question
 * @param createdAt when it started
 * @param updatedAt when it last received a turn
 */
public record ConversationSummaryResponse(UUID id, UUID organizationId, UUID projectId, String projectName,
		String title, Instant createdAt, Instant updatedAt) {

	public static ConversationSummaryResponse from(ChatConversation conversation, String projectName) {
		return new ConversationSummaryResponse(conversation.getId(), conversation.getOrganizationId(),
				conversation.getProjectId(), projectName, conversation.getTitle(), conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}

}
