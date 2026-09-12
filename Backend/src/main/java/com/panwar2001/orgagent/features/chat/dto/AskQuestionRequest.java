package com.panwar2001.orgagent.features.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question asked of a project's documents.
 *
 * @param question the question text
 * @param conversationId continues an existing conversation; omit to start a new one
 */
public record AskQuestionRequest(@NotBlank @Size(max = 4000) String question, UUID conversationId) {
}
