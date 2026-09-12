package com.panwar2001.orgagent.features.chat.dto;

import java.util.UUID;

import com.panwar2001.orgagent.features.chat.rag.RetrievedChunk;

/**
 * A document passage the answer was grounded in.
 *
 * @param documentId the source document
 * @param title the source document title
 * @param score cosine similarity between the question and the passage
 */
public record ChatSource(UUID documentId, String title, Double score) {

	public static ChatSource from(RetrievedChunk chunk) {
		return new ChatSource(parse(chunk.documentId()), chunk.title(), chunk.score());
	}

	private static UUID parse(String documentId) {
		try {
			return documentId == null ? null : UUID.fromString(documentId);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
