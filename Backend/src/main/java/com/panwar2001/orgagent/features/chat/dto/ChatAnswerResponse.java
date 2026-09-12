package com.panwar2001.orgagent.features.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The answer to a question.
 *
 * @param conversationId conversation to continue with the next question
 * @param answer the generated (or cached) answer
 * @param fromCache true when the semantic cache answered without calling the model
 * @param sources the document passages the answer was grounded in; empty for a cache hit
 * @param model the model that produced the answer; null for a cache hit
 * @param latencyMs how long the model call took; null for a cache hit
 * @param answeredAt when the answer was produced
 */
public record ChatAnswerResponse(UUID conversationId, String answer, boolean fromCache, List<ChatSource> sources,
		String model, Long latencyMs, Instant answeredAt) {
}
