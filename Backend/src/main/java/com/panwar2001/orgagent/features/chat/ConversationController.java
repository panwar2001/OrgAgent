package com.panwar2001.orgagent.features.chat;

import java.util.UUID;

import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.core.web.Pagination;
import com.panwar2001.orgagent.features.chat.dto.ConversationSummaryResponse;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Organization-level view of conversations.
 *
 * <p>Conversations belong to a project, but a person works across projects: this lists them all for
 * one organization, newest first, which is what the console's session rail shows.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/conversations")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Ask questions grounded in the project's documents")
public class ConversationController {

	private final ChatService service;

	@GetMapping
	@Operation(summary = "List every conversation of an organization, across its projects")
	public PageResponse<ConversationSummaryResponse> list(@PathVariable UUID organizationId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
		return this.service.organizationConversations(organizationId,
				Pagination.of(page, size, "updatedAt", Sort.Direction.DESC));
	}

}
