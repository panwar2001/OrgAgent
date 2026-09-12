package com.panwar2001.orgagent.features.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;
import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.IngestionException;
import com.panwar2001.orgagent.core.exception.PayloadTooLargeException;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.exception.UnsupportedMediaTypeException;
import com.panwar2001.orgagent.features.ingestion.dto.DocumentResponse;
import com.panwar2001.orgagent.features.ingestion.extract.DocumentTextExtractor;
import com.panwar2001.orgagent.features.ingestion.extract.DocumentTextExtractors;
import com.panwar2001.orgagent.features.project.Project;
import com.panwar2001.orgagent.features.project.ProjectService;
import com.panwar2001.orgagent.features.project.ProjectStatus;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class IngestionServiceTest {

	private final DocumentRepository repository = mock(DocumentRepository.class);

	private final ProjectService projectService = mock(ProjectService.class);

	private final DocumentTextExtractors extractors = mock(DocumentTextExtractors.class);

	private final TextChunker chunker = mock(TextChunker.class);

	private final VectorStore vectorStore = mock(VectorStore.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private final IngestionService service = new IngestionService(this.repository, this.projectService,
			this.extractors, this.chunker, this.vectorStore, properties(DataSize.ofMegabytes(1)));

	@Test
	void storesTheDocumentAndEmbedsItsChunks() {
		givenActiveProject();
		givenNoDuplicate();
		when(this.extractors.extract(any(), any(), any())).thenReturn("Refunds take five working days.");
		when(this.chunker.chunk(any())).thenReturn(List.of("chunk one", "chunk two"));
		when(this.repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

		DocumentResponse response = this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt"));

		assertThat(response.status()).isEqualTo(DocumentStatus.INDEXED);
		assertThat(response.chunkCount()).isEqualTo(2);
		assertThat(response.title()).isEqualTo("policy");
		assertThat(response.fileName()).isEqualTo("policy.txt");

		verify(this.vectorStore).add(anyList());
	}

	@Test
	void tagsEveryChunkWithTheTenantProjectAndDocumentSoRetrievalCanFilter() {
		givenActiveProject();
		givenNoDuplicate();
		when(this.extractors.extract(any(), any(), any())).thenReturn("text");
		when(this.chunker.chunk(any())).thenReturn(List.of("chunk one", "chunk two"));
		when(this.repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

		this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<org.springframework.ai.document.Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.vectorStore).add(captor.capture());

		assertThat(captor.getValue()).hasSize(2).allSatisfy(document -> {
			assertThat(document.getMetadata()).containsEntry(IngestionService.META_PROJECT_ID, this.projectId.toString())
				.containsEntry(IngestionService.META_ORGANIZATION_ID, this.organizationId.toString())
				.containsEntry(IngestionService.META_TITLE, "policy")
				.containsEntry(IngestionService.META_PROJECT_SLUG, "hr");
		});
		assertThat(captor.getValue()).extracting(document -> document.getMetadata().get("chunkIndex"))
			.containsExactly(0, 1);
		assertThat(captor.getValue()).extracting(org.springframework.ai.document.Document::getText)
			.containsExactly("chunk one", "chunk two");
	}

	@Test
	void refusesTheSameFileTwiceInTheSameProject() {
		givenActiveProject();
		when(this.repository.existsByProjectIdAndContentHash(eq(this.projectId), any())).thenReturn(true);

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(ConflictException.class)
			.satisfies(ex -> assertThat(((ConflictException) ex).code()).isEqualTo(ErrorCode.DOCUMENT_ALREADY_EXISTS));

		verify(this.repository, never()).save(any());
		verify(this.vectorStore, never()).add(anyList());
	}

	@Test
	void refusesFilesAboveTheConfiguredSize() {
		IngestionService smallLimit = new IngestionService(this.repository, this.projectService, this.extractors,
				this.chunker, this.vectorStore, properties(DataSize.ofBytes(4)));
		givenActiveProject();

		assertThatThrownBy(() -> smallLimit.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(PayloadTooLargeException.class)
			.hasMessageContaining("limit is 4B");
	}

	@Test
	void refusesAnEmptyUpload() {
		givenActiveProject();

		MockMultipartFile empty = new MockMultipartFile("file", "policy.txt", "text/plain", new byte[0]);

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, empty))
			.isInstanceOf(com.panwar2001.orgagent.core.exception.BadRequestException.class);
	}

	@Test
	void refusesAProjectThatDoesNotExist() {
		when(this.projectService.require(this.organizationId, this.projectId))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.PROJECT_NOT_FOUND, this.projectId));

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void refusesToIngestIntoAnArchivedProject() {
		when(this.projectService.require(this.organizationId, this.projectId))
			.thenReturn(Project.of(this.projectId, this.organizationId, "HR", "hr", "d", ProjectStatus.ARCHIVED));

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("archived");
	}

	@Test
	void refusesAFileThatHasNoReadableText() {
		givenActiveProject();
		givenNoDuplicate();
		when(this.repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
		when(this.extractors.extract(any(), any(), any())).thenReturn("   ");

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(com.panwar2001.orgagent.core.exception.BadRequestException.class)
			.hasMessageContaining("No readable text");
	}

	@Test
	void surfacesAnUnsupportedFormatAndRecordsTheFailure() {
		givenActiveProject();
		givenNoDuplicate();
		when(this.repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
		when(this.extractors.extract(any(), any(), any()))
			.thenThrow(new UnsupportedMediaTypeException("Cannot read 'archive.zip'"));

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("archive.zip")))
			.isInstanceOf(UnsupportedMediaTypeException.class);

		assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.FAILED);
	}

	@Test
	void marksTheDocumentFailedWhenEmbeddingBreaks() {
		givenActiveProject();
		givenNoDuplicate();
		when(this.repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
		when(this.extractors.extract(any(), any(), any())).thenReturn("text");
		when(this.chunker.chunk(any())).thenReturn(List.of("chunk"));
		org.mockito.Mockito.doThrow(new IllegalStateException("model provider unavailable"))
			.when(this.vectorStore)
			.add(anyList());

		assertThatThrownBy(() -> this.service.ingest(this.organizationId, this.projectId, textFile("policy.txt")))
			.isInstanceOf(IngestionException.class);

		Document failed = savedDocument();
		assertThat(failed.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(failed.getErrorMessage()).isEqualTo("model provider unavailable");
	}

	@Test
	void deletesTheEmbeddingsTogetherWithTheDocument() {
		UUID documentId = UUID.randomUUID();
		Document document = Document.of(documentId, this.organizationId, this.projectId, "HR", "hr.txt", "text/plain",
				DocumentStatus.INDEXED, 3);
		when(this.repository.findByIdAndOrganizationIdAndProjectId(documentId, this.organizationId, this.projectId))
			.thenReturn(Optional.of(document));

		this.service.delete(this.organizationId, this.projectId, documentId);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Filter.Expression> filter = ArgumentCaptor.forClass(Filter.Expression.class);
		verify(this.vectorStore).delete(filter.capture());
		assertThat(filter.getValue().toString()).contains(documentId.toString());
		verify(this.repository).delete(document);
	}

	@Test
	void readsOneDocumentScopedToItsProject() {
		UUID documentId = UUID.randomUUID();
		when(this.repository.findByIdAndOrganizationIdAndProjectId(documentId, this.organizationId, this.projectId))
			.thenReturn(Optional.of(Document.of(documentId, this.organizationId, this.projectId, "HR", "hr.txt",
					"text/plain", DocumentStatus.INDEXED, 3)));

		assertThat(this.service.get(this.organizationId, this.projectId, documentId).id()).isEqualTo(documentId);
	}

	@Test
	void refusesToReadADocumentOfAnotherProject() {
		UUID documentId = UUID.randomUUID();
		when(this.repository.findByIdAndOrganizationIdAndProjectId(documentId, this.organizationId, this.projectId))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.get(this.organizationId, this.projectId, documentId))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	private void givenActiveProject() {
		when(this.projectService.require(this.organizationId, this.projectId))
			.thenReturn(Project.of(this.projectId, this.organizationId, "HR", "hr", "d", ProjectStatus.ACTIVE));
	}

	private void givenNoDuplicate() {
		when(this.repository.existsByProjectIdAndContentHash(eq(this.projectId), any())).thenReturn(false);
	}

	private Document savedDocument() {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		verify(this.repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		return captor.getValue();
	}

	private MockMultipartFile textFile(String fileName) {
		return new MockMultipartFile("file", fileName, "text/plain", "Refunds take five working days.".getBytes());
	}

	private OrgAgentProperties properties(DataSize maxFileSize) {
		OrgAgentProperties defaults = TestProperties.defaults();
		return new OrgAgentProperties(defaults.rag(), defaults.cache(),
				new OrgAgentProperties.Ingestion(maxFileSize, 800, 120), defaults.cors());
	}

}
