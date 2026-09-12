package com.panwar2001.orgagent.core.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The two pgvector stores this service talks to.
 *
 * <p>They are declared here rather than left to Spring AI's auto-configuration for two reasons:
 * there are two of them (document chunks and the semantic answer cache), which the auto-configuration
 * cannot express, and their tables are created by Flyway, so schema initialisation must stay off.
 *
 * <p>Both tables have the layout {@code PgVectorStore} expects — {@code id, content, metadata,
 * embedding} — and identical vector widths, so a question embedded for retrieval is directly
 * comparable with one embedded for the cache.
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

	/** Name of the store holding the embedded document chunks. */
	public static final String DOCUMENT_VECTOR_STORE = "vectorStore";

	/** Name of the store holding question/answer pairs. */
	public static final String SEMANTIC_CACHE_VECTOR_STORE = "semanticCacheVectorStore";

	/** Table created by {@code V2__documents_and_embeddings.sql}. */
	static final String DOCUMENT_TABLE = "document_embeddings";

	/** Table created by {@code V3__chat.sql}. */
	static final String SEMANTIC_CACHE_TABLE = "chat_semantic_cache";

	@Bean(name = DOCUMENT_VECTOR_STORE)
	@Primary
	PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OrgAgentProperties properties) {
		return store(jdbcTemplate, embeddingModel, properties, DOCUMENT_TABLE);
	}

	@Bean(name = SEMANTIC_CACHE_VECTOR_STORE)
	VectorStore semanticCacheVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OrgAgentProperties properties) {
		return store(jdbcTemplate, embeddingModel, properties, SEMANTIC_CACHE_TABLE);
	}

	private PgVectorStore store(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OrgAgentProperties properties, String tableName) {
		return PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.vectorTableName(tableName)
			.dimensions(properties.rag().embeddingDimensions())
			.distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
			.indexType(PgVectorStore.PgIndexType.HNSW)
			.initializeSchema(false)
			.build();
	}

}
