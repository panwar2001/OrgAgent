package com.panwar2001.orgagent.features.chat.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

class SemanticAnswerCacheTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private final SemanticAnswerCache cache = new SemanticAnswerCache(this.jdbcTemplate, this.embeddingModel,
			JsonMapper.builder().build(), TestProperties.defaults());

	@Test
	void returnsACachedAnswerForAnEquivalentQuestion() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f, 0.2f });
		when(this.jdbcTemplate.queryForList(anyString(), any(Object[].class)))
			.thenReturn(List.of(Map.of("content", "Five working days.", "similarity", 0.99)));

		assertThat(cache.find(this.organizationId, this.projectId, "how long do refunds take?"))
			.contains("Five working days.");
	}

	@Test
	void scopesTheLookupToTheProjectAndToLiveEntries() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f });
		when(this.jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

		cache.find(this.organizationId, this.projectId, "question");

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(this.jdbcTemplate).queryForList(anyString(), arguments.capture());

		// embedding literal, project scope, not-expired cut-off, embedding literal for ordering
		assertThat(arguments.getValue()).hasSize(4);
		assertThat(arguments.getValue()[0]).isEqualTo("[0.1]");
		assertThat(arguments.getValue()[1]).isEqualTo(this.projectId.toString());
		assertThat((Long) arguments.getValue()[2]).isLessThanOrEqualTo(java.time.Instant.now().getEpochSecond());
	}

	@Test
	void treatsAnEntryThatIsNotSimilarEnoughAsAMiss() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f });
		when(this.jdbcTemplate.queryForList(anyString(), any(Object[].class)))
			.thenReturn(List.of(Map.of("content", "unrelated", "similarity", 0.42)));

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
	}

	@Test
	void reportsAMissWhenNothingIsCached() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f });
		when(this.jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
	}

	@Test
	void degradesToAMissWhenTheQuestionCannotBeEmbedded() {
		// A cache is an optimisation: a provider outage must not fail the chat turn.
		when(this.embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("400 API key not valid"));

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
		verify(this.jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
	}

	@Test
	void degradesToAMissWhenTheCacheCannotBeRead() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f });
		when(this.jdbcTemplate.queryForList(anyString(), any(Object[].class)))
			.thenThrow(new IllegalStateException("connection reset"));

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
	}

	@Test
	void storesTheQuestionEmbeddingSoTheNextLookupCanFindIt() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.5f, -0.25f });

		cache.store(this.organizationId, this.projectId, "how long do refunds take?", "Five working days.");

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(this.jdbcTemplate).update(argThat(sql -> sql.startsWith("INSERT INTO chat_semantic_cache")),
				arguments.capture());

		Object[] values = arguments.getValue();
		assertThat(values[1]).isEqualTo("Five working days.");
		assertThat(values[3]).isEqualTo("[0.5,-0.25]");
		assertThat((String) values[2]).contains(this.projectId.toString())
			.contains("how long do refunds take?")
			.contains(SemanticAnswerCache.META_EXPIRES_AT)
			.contains(SemanticAnswerCache.META_CREATED_AT);
	}

	@Test
	void purgesExpiredEntriesWhenItWrites() {
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.5f });

		cache.store(this.organizationId, this.projectId, "q", "a");

		verify(this.jdbcTemplate).update(argThat(sql -> sql.startsWith("INSERT INTO chat_semantic_cache")),
				any(Object[].class));
		verify(this.jdbcTemplate).update(argThat(sql -> sql.startsWith("DELETE FROM chat_semantic_cache")),
				eq(java.time.Instant.now().getEpochSecond()));
	}

	@Test
	void neverStoresAnEmptyAnswerOrQuestion() {
		cache.store(this.organizationId, this.projectId, "question", "   ");
		cache.store(this.organizationId, this.projectId, "   ", "answer");

		verify(this.jdbcTemplate, never()).update(anyString(), any(Object[].class));
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void doesNothingAtAllWhenTheCacheIsSwitchedOff() {
		OrgAgentProperties defaults = TestProperties.defaults();
		SemanticAnswerCache disabled = new SemanticAnswerCache(this.jdbcTemplate, this.embeddingModel,
				JsonMapper.builder().build(),
				new OrgAgentProperties(defaults.rag(),
						new OrgAgentProperties.Cache(new OrgAgentProperties.Cache.Semantic(false, 0.95,
								Duration.ofDays(1), 100)),
						defaults.ingestion(), defaults.cors()));

		assertThat(disabled.enabled()).isFalse();
		assertThat(disabled.find(this.organizationId, this.projectId, "question")).isEmpty();

		disabled.store(this.organizationId, this.projectId, "question", "answer");

		verify(this.embeddingModel, never()).embed(anyString());
		verify(this.jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
	}

	@Test
	void rendersVectorsInTheFormatPgvectorAccepts() {
		assertThat(SemanticAnswerCache.toVectorLiteral(new float[] { 1f, -0.5f, 0f })).isEqualTo("[1.0,-0.5,0.0]");
	}

}
