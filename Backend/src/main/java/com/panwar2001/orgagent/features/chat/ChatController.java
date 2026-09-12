package com.panwar2001.orgagent.features.chat;

import java.util.UUID;

import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.core.web.Pagination;
import com.panwar2001.orgagent.features.chat.dto.AskQuestionRequest;
import com.panwar2001.orgagent.features.chat.dto.ChatAnswerResponse;
import com.panwar2001.orgagent.features.chat.dto.ChatMessageResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationWindowResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Ask questions grounded in the project's documents")
public class ChatController {

	private final ChatService service;

	@PostMapping
	@Operation(summary = "Ask a question about the project's documents")
	public ChatAnswerResponse ask(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@Valid @RequestBody AskQuestionRequest request) {
		return this.service.ask(organizationId, projectId, request);
	}

	@GetMapping
	@Operation(summary = "List the conversations of a project")
	public PageResponse<ConversationResponse> conversations(@PathVariable UUID organizationId,
			@PathVariable UUID projectId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.service.conversations(organizationId, projectId, Pagination.of(page, size, "updatedAt"));
	}

	@GetMapping("/{conversationId}")
	@Operation(summary = "Read the live window of a conversation, served from Redis")
	public ConversationWindowResponse window(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@PathVariable UUID conversationId) {
		return this.service.window(organizationId, projectId, conversationId);
	}

	@GetMapping("/{conversationId}/history")
	@Operation(summary = "Read the permanent log of a conversation from Postgres")
	public PageResponse<ChatMessageResponse> history(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@PathVariable UUID conversationId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return this.service.history(organizationId, projectId, conversationId,
				Pagination.of(page, size, "createdAt"));
	}

	@DeleteMapping("/{conversationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a conversation, its live window and its log")
	public void delete(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@PathVariable UUID conversationId) {
		this.service.delete(organizationId, projectId, conversationId);
	}

}
