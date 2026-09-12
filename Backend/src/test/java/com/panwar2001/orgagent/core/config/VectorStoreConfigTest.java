package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache;
import com.panwar2001.orgagent.features.chat.rag.RagRetriever;

/**
 * Verifies the wiring that is easiest to get silently wrong: two stores of the same type, injected
 * by qualifier, in a context with no database behind them.
 */
class VectorStoreConfigTest {

	@Test
	void declaresSeparateStoresForDocumentChunksAndCachedAnswers() {
		try (AnnotationConfigApplicationContext context = context()) {
			assertThat(context.getBeanNamesForType(VectorStore.class))
				.containsExactlyInAnyOrder(VectorStoreConfig.DOCUMENT_VECTOR_STORE,
						VectorStoreConfig.SEMANTIC_CACHE_VECTOR_STORE);
			assertThat(context.getBean(VectorStoreConfig.DOCUMENT_VECTOR_STORE)).isNotNull();
			assertThat(context.getBean(VectorStoreConfig.SEMANTIC_CACHE_VECTOR_STORE)).isNotNull();
			assertThat(context.getBean(VectorStoreConfig.DOCUMENT_VECTOR_STORE))
				.isNotSameAs(context.getBean(VectorStoreConfig.SEMANTIC_CACHE_VECTOR_STORE));
		}
	}

	@Test
	void wiresRetrievalToTheDocumentStoreAndTheCacheToItsOwnStore() {
		try (AnnotationConfigApplicationContext context = context(RagRetriever.class, SemanticAnswerCache.class)) {
			RagRetriever retriever = context.getBean(RagRetriever.class);
			SemanticAnswerCache cache = context.getBean(SemanticAnswerCache.class);

			assertThat(retriever).isNotNull();
			assertThat(cache).isNotNull();
			assertThat(cache.enabled()).isTrue();
		}
	}

	private AnnotationConfigApplicationContext context(Class<?>... additional) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
		context.registerBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));
		context.registerBean(OrgAgentProperties.class, TestProperties::defaults);
		context.register(VectorStoreConfig.class);
		if (additional.length > 0) {
			context.register(additional);
		}
		context.refresh();
		return context;
	}

}
