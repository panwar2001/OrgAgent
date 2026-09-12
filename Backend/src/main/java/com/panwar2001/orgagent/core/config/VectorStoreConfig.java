package com.panwar2001.orgagent.core.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The pgvector store holding embedded document chunks.
 *
 * <p>It is declared here rather than left to Spring AI's auto-configuration so its table and
 * vector width come from this service's configuration, and because Flyway owns the schema
 * ({@code initializeSchema(false)}).
 *
 * <p>The semantic answer cache is not a vector store: it embeds questions itself, through
 * {@link com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache}, so that the same task
 * type is used on write and on read.
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

	/** Name of the store holding the embedded document chunks. */
	public static final String DOCUMENT_VECTOR_STORE = "vectorStore";

	/** Table created by {@code V2__documents_and_embeddings.sql}. */
	static final String DOCUMENT_TABLE = "document_embeddings";

	@Bean(name = DOCUMENT_VECTOR_STORE)
	@Primary
	PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OrgAgentProperties properties) {
		return store(jdbcTemplate, embeddingModel, properties, DOCUMENT_TABLE);
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
