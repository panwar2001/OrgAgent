package com.panwar2001.orgagent.core.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Every knob this service exposes, bound from the {@code orgagent.*} tree in
 * {@code application.yaml}.
 *
 * <p>Having them in one immutable record keeps the RAG tuning (window size, retrieval breadth,
 * cache thresholds, chunking) reviewable instead of scattered as {@code @Value} strings.
 */
@Validated
@ConfigurationProperties(prefix = "orgagent")
public record OrgAgentProperties(@Valid Rag rag, @Valid Cache cache, @Valid Ingestion ingestion, @Valid Cors cors) {

	/**
	 * Retrieval-augmented generation tuning.
	 *
	 * @param chatWindowSize how many recent turns are replayed from Redis into the prompt
	 * @param chatWindowTtl TTL of the live Redis conversation window
	 * @param retrievalTopK chunks pulled out of pgvector per question
	 * @param retrievalSimilarityThreshold minimum cosine similarity for a chunk to be used
	 */
	public record Rag(
			@DefaultValue("20") @Min(1) @Max(200) int chatWindowSize,
			@DefaultValue("PT24H") @NotNull Duration chatWindowTtl,
			@DefaultValue("6") @Min(1) @Max(50) int retrievalTopK,
			@DefaultValue("0.6") @Positive double retrievalSimilarityThreshold) {
	}

	/** Semantic cache tuning. */
	public record Cache(@Valid Semantic semantic) {

		/**
		 * @param enabled whether answered questions may be served from the vector cache
		 * @param similarityThreshold cosine similarity above which a cached answer is a hit
		 * @param ttl how long a cached answer stays valid
		 * @param maxEntriesPerProject cap on cached answers kept per project
		 */
		public record Semantic(
				@DefaultValue("true") boolean enabled,
				@DefaultValue("0.95") @Positive double similarityThreshold,
				@DefaultValue("P7D") @NotNull Duration ttl,
				@DefaultValue("5000") @Min(1) int maxEntriesPerProject) {
		}

	}

	/** Document ingestion tuning. */
	public record Ingestion(
			@DefaultValue("25MB") @NotNull DataSize maxFileSize,
			@DefaultValue("800") @Min(100) @Max(8000) int chunkSize,
			@DefaultValue("120") @Min(0) int chunkOverlap) {
	}

	/** Cross-origin policy applied by {@link CorsConfig}. */
	public record Cors(
			@NotEmpty List<String> allowedOrigins,
			@NotEmpty List<String> allowedMethods,
			@NotEmpty List<String> allowedHeaders,
			List<String> exposedHeaders,
			@DefaultValue("true") boolean allowCredentials,
			@DefaultValue("PT1H") @NotNull Duration maxAge) {
	}

}
