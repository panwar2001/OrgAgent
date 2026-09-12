package com.panwar2001.orgagent.features.chat.rag;

import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.VectorStoreConfig;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.LlmException;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Vector search over the documents of one project.
 *
 * <p>The question is embedded by the vector store itself (it owns the embedding model), and the
 * search is filtered on the project id so a question can never be answered from another tenant's
 * documents, however similar they are.
 */
@Slf4j
@Component
public class RagRetriever {

	/** Metadata key the ingestion pipeline writes onto every chunk. */
	static final String META_PROJECT_ID = "projectId";

	static final String META_DOCUMENT_ID = "documentId";

	static final String META_TITLE = "title";

	private final VectorStore vectorStore;

	private final OrgAgentProperties properties;

	public RagRetriever(@Qualifier(VectorStoreConfig.DOCUMENT_VECTOR_STORE) VectorStore vectorStore, OrgAgentProperties properties) {
		this.vectorStore = vectorStore;
		this.properties = properties;
	}

	/**
	 * @param projectId project whose documents may be searched
	 * @param question natural-language question
	 * @return the most similar passages above the configured similarity threshold, best first
	 */
	public List<RetrievedChunk> retrieve(UUID projectId, String question) {
		SearchRequest request = SearchRequest.builder()
			.query(question)
			.topK(this.properties.rag().retrievalTopK())
			.similarityThreshold(this.properties.rag().retrievalSimilarityThreshold())
			.filterExpression(new FilterExpressionBuilder().eq(META_PROJECT_ID, projectId.toString()).build())
			.build();

		List<org.springframework.ai.document.Document> matches;
		try {
			matches = this.vectorStore.similaritySearch(request);
		}
		catch (RuntimeException failure) {
			// The store embeds the question before searching, so a provider outage surfaces here.
			throw new LlmException(ErrorCode.EMBEDDING_FAILED,
					"The question could not be embedded for retrieval", failure);
		}

		List<RetrievedChunk> chunks = matches.stream().map(RagRetriever::toChunk).toList();
		log.debug("Retrieved {} passage(s) for project {}", chunks.size(), projectId);
		return chunks;
	}

	private static RetrievedChunk toChunk(org.springframework.ai.document.Document document) {
		Object documentId = document.getMetadata().get(META_DOCUMENT_ID);
		Object title = document.getMetadata().get(META_TITLE);
		return new RetrievedChunk(documentId == null ? null : documentId.toString(),
				title == null ? "Untitled document" : title.toString(), document.getText(), document.getScore());
	}

}
