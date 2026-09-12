package com.panwar2001.orgagent.features.chat.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers already given for a semantically equivalent question.
 *
 * <p>Every lookup embeds the question and compares it with the questions answered before, so
 * "how long do refunds take?" reaches the answer stored for "what is the refund window?". A hit
 * skips both retrieval and the model call, which is the difference between a fraction of a second
 * and a billed round trip.
 *
 * <p>The embedding is produced here rather than through a {@code VectorStore}, because that is the
 * only way to embed the question the same way on write and on read. The embedding model uses
 * different task types for documents and queries, and the same text scores about 0.925 across the
 * two — below the cache threshold — so a store that embeds cached entries as documents can never
 * recognise even an identical question. Retrieval keeps its document/query asymmetry; the cache
 * keys on questions in both directions.
 *
 * <p>Scoping is by project, mirroring retrieval: a cached answer from another tenant's project is
 * never visible. Expiry is applied in SQL, so a stale entry is a miss rather than a failure.
 */
@Slf4j
@Component
public class SemanticAnswerCache {

	static final String META_ORGANIZATION_ID = "organizationId";

	static final String META_PROJECT_ID = "projectId";

	static final String META_QUESTION = "question";

	static final String META_EXPIRES_AT = "expiresAtEpochSeconds";

	static final String META_CREATED_AT = "createdAtEpochSeconds";

	private static final String SELECT_NEAREST = """
			SELECT content, 1 - (embedding <=> CAST(? AS vector)) AS similarity
			FROM chat_semantic_cache
			WHERE metadata ->> 'projectId' = ?
			  AND (metadata ->> 'expiresAtEpochSeconds')::bigint >= ?
			ORDER BY embedding <=> CAST(? AS vector)
			LIMIT 1
			""";

	private static final String INSERT_ANSWER = """
			INSERT INTO chat_semantic_cache (id, content, metadata, embedding)
			VALUES (CAST(? AS uuid), ?, CAST(? AS json), CAST(? AS vector))
			""";

	private static final String PURGE_EXPIRED = """
			DELETE FROM chat_semantic_cache
			WHERE (metadata ->> 'expiresAtEpochSeconds')::bigint < ?
			""";

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingModel embeddingModel;

	private final ObjectMapper json;

	private final OrgAgentProperties properties;

	public SemanticAnswerCache(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, ObjectMapper json,
			OrgAgentProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.embeddingModel = embeddingModel;
		this.json = json;
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

		String embedding;
		try {
			embedding = toVectorLiteral(this.embeddingModel.embed(question));
		}
		catch (RuntimeException failure) {
			// A cache is an optimisation: if it cannot be consulted, answer the question anyway.
			log.warn("Semantic cache lookup skipped, could not embed the question: {}", failure.getMessage());
			return Optional.empty();
		}

		try {
			List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(SELECT_NEAREST, embedding,
					projectId.toString(), Instant.now().getEpochSecond(), embedding);
			if (rows.isEmpty()) {
				return Optional.empty();
			}

			Map<String, Object> nearest = rows.get(0);
			double similarity = nearest.get("similarity") instanceof Number number ? number.doubleValue() : 0d;
			double threshold = this.properties.cache().semantic().similarityThreshold();
			if (similarity < threshold) {
				log.debug("Nearest cached question scores {} against a threshold of {} for project {}", similarity,
						threshold, projectId);
				return Optional.empty();
			}

			log.debug("Semantic cache hit for project {} (similarity {})", projectId, similarity);
			return Optional.ofNullable((String) nearest.get("content"));
		}
		catch (RuntimeException failure) {
			log.warn("Semantic cache lookup failed, continuing without it: {}", failure.getMessage());
			return Optional.empty();
		}
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
		if (!enabled() || question == null || question.isBlank() || answer == null || answer.isBlank()) {
			return;
		}

		Instant now = Instant.now();
		long expiresAt = now.plus(this.properties.cache().semantic().ttl()).getEpochSecond();

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(META_ORGANIZATION_ID, organizationId.toString());
		metadata.put(META_PROJECT_ID, projectId.toString());
		metadata.put(META_QUESTION, question);
		metadata.put(META_CREATED_AT, now.getEpochSecond());
		metadata.put(META_EXPIRES_AT, expiresAt);

		try {
			String embedding = toVectorLiteral(this.embeddingModel.embed(question));
			this.jdbcTemplate.update(INSERT_ANSWER, UUID.randomUUID().toString(), answer,
					this.json.writeValueAsString(metadata), embedding);
			// Opportunistic housekeeping: keep the table from growing with dead entries.
			this.jdbcTemplate.update(PURGE_EXPIRED, now.getEpochSecond());
		}
		catch (RuntimeException failure) {
			log.warn("Could not populate the semantic cache for project {}: {}", projectId, failure.getMessage());
		}
	}

	/** pgvector's text input format, e.g. {@code [0.1,-0.2]}. */
	static String toVectorLiteral(float[] embedding) {
		StringBuilder literal = new StringBuilder(embedding.length * 8).append('[');
		for (int index = 0; index < embedding.length; index++) {
			if (index > 0) {
				literal.append(',');
			}
			literal.append(Float.toString(embedding[index]));
		}
		return literal.append(']').toString();
	}

}
