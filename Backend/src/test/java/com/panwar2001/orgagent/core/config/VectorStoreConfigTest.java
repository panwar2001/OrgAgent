package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.panwar2001.orgagent.features.chat.rag.RagRetriever;

/**
 * Verifies the wiring that is easiest to get silently wrong, in a context with no database behind
 * it: retrieval must be bound to the document store, and only that store exists.
 */
class VectorStoreConfigTest {

	@Test
	void declaresASingleStoreForDocumentChunks() {
		try (AnnotationConfigApplicationContext context = context()) {
			assertThat(context.getBeanNamesForType(VectorStore.class))
				.containsExactly(VectorStoreConfig.DOCUMENT_VECTOR_STORE);
			assertThat(context.getBean(VectorStoreConfig.DOCUMENT_VECTOR_STORE)).isNotNull();
		}
	}

	@Test
	void wiresRetrievalToTheDocumentStore() {
		try (AnnotationConfigApplicationContext context = context(RagRetriever.class)) {
			assertThat(context.getBean(RagRetriever.class)).isNotNull();
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
