package com.panwar2001.orgagent.features.chat.rag;

/**
 * One passage retrieved from pgvector for a question.
 *
 * @param documentId the document the passage came from
 * @param title the document title, for citation in the answer
 * @param text the passage itself
 * @param score cosine similarity between the question and the passage, when the store reports it
 */
public record RetrievedChunk(String documentId, String title, String text, Double score) {
}
