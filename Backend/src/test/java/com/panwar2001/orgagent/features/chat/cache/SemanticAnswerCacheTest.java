package com.panwar2001.orgagent.features.chat.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class SemanticAnswerCacheTest {

	private final VectorStore cacheStore = mock(VectorStore.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private final SemanticAnswerCache cache = new SemanticAnswerCache(this.cacheStore, TestProperties.defaults());

	@Test
	void returnsACachedAnswerForAnEquivalentQuestion() {
		when(this.cacheStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(entry("Refunds take five working days.", future())));

		assertThat(cache.find(this.organizationId, this.projectId, "how long do refunds take?"))
			.contains("Refunds take five working days.");
	}

	@Test
	void scopesTheLookupToTheProjectSoAnswersNeverLeakAcrossTenants() {
		when(this.cacheStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		cache.find(this.organizationId, this.projectId, "question");

		ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
		verify(this.cacheStore).similaritySearch(request.capture());
		assertThat(request.getValue().getFilterExpression().toString()).contains(this.projectId.toString());
		assertThat(request.getValue().getTopK()).isEqualTo(1);
		assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.95);
	}

	@Test
	void treatsAStaleEntryAsAMissAndCleansItUp() {
		when(this.cacheStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(entry("outdated answer", Instant.now().getEpochSecond() - 60)));

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();

		verify(this.cacheStore).delete(List.of("cache-1"));
	}

	@Test
	void degradesToAMissWhenTheCacheCannotBeRead() {
		// A cache is an optimisation: a provider or store outage must not fail the chat turn.
		when(this.cacheStore.similaritySearch(any(SearchRequest.class)))
			.thenThrow(new IllegalStateException("400 API key not valid"));

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
	}

	@Test
	void reportsAMissWhenNothingIsSimilarEnough() {
		when(this.cacheStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		assertThat(cache.find(this.organizationId, this.projectId, "question")).isEmpty();
	}

	@Test
	void storesTheAnswerWithTheMetadataNeededToFindItAgain() {
		cache.store(this.organizationId, this.projectId, "how long do refunds take?", "Five working days.");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.cacheStore).add(captor.capture());

		Document entry = captor.getValue().get(0);
		assertThat(entry.getText()).isEqualTo("Five working days.");
		assertThat(entry.getMetadata()).containsEntry(SemanticAnswerCache.META_PROJECT_ID, this.projectId.toString())
			.containsEntry(SemanticAnswerCache.META_ORGANIZATION_ID, this.organizationId.toString())
			.containsEntry(SemanticAnswerCache.META_QUESTION, "how long do refunds take?")
			.containsKey(SemanticAnswerCache.META_EXPIRES_AT)
			.containsKey(SemanticAnswerCache.META_CREATED_AT);
	}

	@Test
	void neverCachesAnEmptyAnswer() {
		cache.store(this.organizationId, this.projectId, "question", "   ");

		verify(this.cacheStore, never()).add(anyList());
	}

	@Test
	void doesNothingAtAllWhenTheCacheIsSwitchedOff() {
		OrgAgentProperties defaults = TestProperties.defaults();
		SemanticAnswerCache disabled = new SemanticAnswerCache(this.cacheStore,
				new OrgAgentProperties(defaults.rag(),
						new OrgAgentProperties.Cache(new OrgAgentProperties.Cache.Semantic(false, 0.95,
								java.time.Duration.ofDays(1), 100)),
						defaults.ingestion(), defaults.cors()));

		assertThat(disabled.enabled()).isFalse();
		assertThat(disabled.find(this.organizationId, this.projectId, "question")).isEmpty();

		disabled.store(this.organizationId, this.projectId, "question", "answer");

		verify(this.cacheStore, never()).similaritySearch(any(SearchRequest.class));
		verify(this.cacheStore, never()).add(anyList());
	}

	@Test
	void usesTheConfiguredExpiryWhenStoring() {
		Instant before = Instant.now().plus(java.time.Duration.ofDays(7)).minusSeconds(5);

		cache.store(this.organizationId, this.projectId, "q", "a");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.cacheStore).add(captor.capture());
		long expiresAt = Long.parseLong(
				captor.getValue().get(0).getMetadata().get(SemanticAnswerCache.META_EXPIRES_AT).toString());

		assertThat(expiresAt).isGreaterThan(before.getEpochSecond());
	}

	private Document entry(String answer, long expiresAtEpochSeconds) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(SemanticAnswerCache.META_EXPIRES_AT, expiresAtEpochSeconds);
		return new Document("cache-1", answer, metadata);
	}

	private long future() {
		return Instant.now().plusSeconds(3600).getEpochSecond();
	}

}
