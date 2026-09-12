package com.panwar2001.orgagent.features.chat.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.VectorStoreConfig;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Answers already given for a semantically equivalent question.
 *
 * <p>Every cache lookup is a pgvector search: the question is embedded and compared against the
 * questions that were answered before, so "how long do refunds take?" hits the answer stored for
 * "what is the refund window?". A hit skips both retrieval and the model call, which is the
 * difference between a fraction of a second and a billed round trip.
 *
 * <p>Scoping is by project, mirroring retrieval: a cached answer from another tenant's project is
 * never visible. Entries carry their own expiry, checked here rather than in the database, so the
 * cache degrades to a miss instead of failing when an entry is stale.
 */
@Slf4j
@Component
public class SemanticAnswerCache {

	static final String META_ORGANIZATION_ID = "organizationId";

	static final String META_PROJECT_ID = "projectId";

	static final String META_QUESTION = "question";

	static final String META_EXPIRES_AT = "expiresAtEpochSeconds";

	static final String META_CREATED_AT = "createdAtEpochSeconds";

	private final VectorStore cacheStore;

	private final OrgAgentProperties properties;

	public SemanticAnswerCache(@Qualifier(VectorStoreConfig.SEMANTIC_CACHE_VECTOR_STORE) VectorStore cacheStore,
			OrgAgentProperties properties) {
		this.cacheStore = cacheStore;
		this.properties = properties;
	}

	/** True when the semantic cache is switched on. */
	public boolean enabled() {
		return this.properties.cache().semantic().enabled();
	}

	/**
	 * Looks for an answer already given to an equivalent question.
	 *
	 * @param organizationId owning organization
	 * @param projectId project the question belongs to
	 * @param question the question just asked
	 * @return the cached answer, or empty when there is no sufficiently similar live entry
	 */
	public Optional<String> find(UUID organizationId, UUID projectId, String question) {
		if (!enabled()) {
			return Optional.empty();
		}

		SearchRequest request = SearchRequest.builder()
			.query(question)
			.topK(1)
			.similarityThreshold(this.properties.cache().semantic().similarityThreshold())
			.filterExpression(new FilterExpressionBuilder().eq(META_PROJECT_ID, projectId.toString()).build())
			.build();

		List<Document> matches;
		try {
			matches = this.cacheStore.similaritySearch(request);
		}
		catch (RuntimeException failure) {
			// A cache is an optimisation: if it cannot be read, answer the question anyway.
			log.warn("Semantic cache lookup failed, continuing without it: {}", failure.getMessage());
			return Optional.empty();
		}

		if (matches.isEmpty()) {
			return Optional.empty();
		}

		Document match = matches.get(0);
		if (isExpired(match)) {
			log.debug("Discarding expired semantic cache entry {}", match.getId());
			discard(match.getId());
			return Optional.empty();
		}
		log.debug("Semantic cache hit for project {} (score {})", projectId, match.getScore());
		return Optional.ofNullable(match.getText());
	}

	/**
	 * Remembers an answer for future equivalent questions.
	 *
	 * @param organizationId owning organization
	 * @param projectId project the question belongs to
	 * @param question the question that was answered
	 * @param answer the answer to reuse
	 */
	public void store(UUID organizationId, UUID projectId, String question, String answer) {
		if (!enabled() || answer == null || answer.isBlank()) {
			return;
		}

		Instant now = Instant.now();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(META_ORGANIZATION_ID, organizationId.toString());
		metadata.put(META_PROJECT_ID, projectId.toString());
		metadata.put(META_QUESTION, question);
		metadata.put(META_CREATED_AT, now.getEpochSecond());
		metadata.put(META_EXPIRES_AT, now.plus(this.properties.cache().semantic().ttl()).getEpochSecond());

		this.cacheStore.add(List.of(new Document(UUID.randomUUID().toString(), answer, metadata)));
	}

	private boolean isExpired(Document document) {
		Object expiresAt = document.getMetadata().get(META_EXPIRES_AT);
		if (expiresAt == null) {
			return false;
		}
		try {
			return Long.parseLong(expiresAt.toString()) < Instant.now().getEpochSecond();
		}
		catch (NumberFormatException ex) {
			return true;
		}
	}

	private void discard(String documentId) {
		try {
			this.cacheStore.delete(List.of(documentId));
		}
		catch (RuntimeException ex) {
			log.warn("Could not discard stale semantic cache entry {}: {}", documentId, ex.getMessage());
		}
	}

}
