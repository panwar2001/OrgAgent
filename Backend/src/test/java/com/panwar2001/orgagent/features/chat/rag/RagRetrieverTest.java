package com.panwar2001.orgagent.features.chat.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.LlmException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagRetrieverTest {

	private final VectorStore vectorStore = mock(VectorStore.class);

	private final UUID projectId = UUID.randomUUID();

	private final RagRetriever retriever = new RagRetriever(this.vectorStore, TestProperties.defaults());

	@Test
	void scopesTheSearchToTheProjectAndTheConfiguredBreadth() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		this.retriever.retrieve(this.projectId, "How long do refunds take?");

		ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
		org.mockito.Mockito.verify(this.vectorStore).similaritySearch(request.capture());

		OrgAgentProperties.Rag rag = TestProperties.defaults().rag();
		assertThat(request.getValue().getQuery()).isEqualTo("How long do refunds take?");
		assertThat(request.getValue().getTopK()).isEqualTo(rag.retrievalTopK());
		assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(rag.retrievalSimilarityThreshold());
		assertThat(request.getValue().getFilterExpression().toString()).contains(this.projectId.toString());
	}

	@Test
	void mapsMatchesIntoChunksWithTheirSourceDocument() {
		UUID documentId = UUID.randomUUID();
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(Document.builder()
			.id(UUID.randomUUID().toString())
			.text("Refunds take five working days.")
			.metadata(Map.of("documentId", documentId.toString(), "title", "Refund policy"))
			.score(0.91)
			.build()));

		List<RetrievedChunk> chunks = this.retriever.retrieve(this.projectId, "refunds?");

		assertThat(chunks).singleElement().satisfies(chunk -> {
			assertThat(chunk.documentId()).isEqualTo(documentId.toString());
			assertThat(chunk.title()).isEqualTo("Refund policy");
			assertThat(chunk.text()).isEqualTo("Refunds take five working days.");
			assertThat(chunk.score()).isEqualTo(0.91);
		});
	}

	@Test
	void fallsBackToATitleWhenTheChunkHasNoMetadata() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(new Document(UUID.randomUUID().toString(), "bare text", Map.of())));

		assertThat(this.retriever.retrieve(this.projectId, "q")).singleElement()
			.satisfies(chunk -> assertThat(chunk.title()).isEqualTo("Untitled document"));
	}

	@Test
	void reportsAProviderFailureAsBadGatewayRatherThanAnUnexpectedError() {
		// The store embeds the question first, so provider outages surface from this call.
		when(this.vectorStore.similaritySearch(any(SearchRequest.class)))
			.thenThrow(new IllegalStateException("400 API key not valid"));

		assertThatThrownBy(() -> this.retriever.retrieve(this.projectId, "q")).isInstanceOf(LlmException.class)
			.hasMessageContaining("could not be embedded")
			.satisfies(failure -> {
				LlmException exception = (LlmException) failure;
				assertThat(exception.code()).isEqualTo(ErrorCode.EMBEDDING_FAILED);
				assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
			});
	}

	@Test
	void returnsNothingWhenNoPassageIsSimilarEnough() {
		when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		assertThat(this.retriever.retrieve(this.projectId, "q")).isEmpty();
	}

}
