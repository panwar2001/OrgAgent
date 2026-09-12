package com.panwar2001.orgagent.features.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.GlobalRestExceptionHandler;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.ingestion.dto.DocumentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DocumentControllerTest {

	private final IngestionService service = mock(IngestionService.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(this.service))
			.setControllerAdvice(new GlobalRestExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void acceptsAMultipartUploadAndReturns201() throws Exception {
		when(this.service.ingest(eq(this.organizationId), eq(this.projectId), any()))
			.thenReturn(response(UUID.randomUUID(), DocumentStatus.INDEXED, 4));

		MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "refunds".getBytes());

		this.mockMvc
			.perform(multipart("/api/v1/organizations/{organizationId}/projects/{projectId}/documents",
					this.organizationId, this.projectId).file(file))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("INDEXED"))
			.andExpect(jsonPath("$.chunkCount").value(4))
			.andExpect(jsonPath("$.fileName").value("policy.txt"));
	}

	@Test
	void reportsAnUnsupportedFormatAs415() throws Exception {
		when(this.service.ingest(eq(this.organizationId), eq(this.projectId), any()))
			.thenThrow(new com.panwar2001.orgagent.core.exception.UnsupportedMediaTypeException(
					"Cannot read 'archive.zip'"));

		MockMultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", "PK".getBytes());

		this.mockMvc
			.perform(multipart("/api/v1/organizations/{organizationId}/projects/{projectId}/documents",
					this.organizationId, this.projectId).file(file))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void listsTheDocumentsOfAProject() throws Exception {
		when(this.service.list(eq(this.organizationId), eq(this.projectId), any(Pageable.class))).thenReturn(
				PageResponse.from(new PageImpl<>(List.of(response(UUID.randomUUID(), DocumentStatus.INDEXED, 2))),
						value -> value));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/documents", this.organizationId,
					this.projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].chunkCount").value(2));
	}

	@Test
	void readsOneDocument() throws Exception {
		UUID documentId = UUID.randomUUID();
		when(this.service.get(this.organizationId, this.projectId, documentId))
			.thenReturn(response(documentId, DocumentStatus.FAILED, 0));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/documents/{documentId}",
					this.organizationId, this.projectId, documentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void turnsAnUnknownDocumentInto404() throws Exception {
		UUID documentId = UUID.randomUUID();
		when(this.service.get(this.organizationId, this.projectId, documentId))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.DOCUMENT_NOT_FOUND, documentId));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/documents/{documentId}",
					this.organizationId, this.projectId, documentId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
	}

	@Test
	void deletesADocumentAndReturns204() throws Exception {
		UUID documentId = UUID.randomUUID();

		this.mockMvc
			.perform(delete("/api/v1/organizations/{organizationId}/projects/{projectId}/documents/{documentId}",
					this.organizationId, this.projectId, documentId))
			.andExpect(status().isNoContent());

		verify(this.service).delete(this.organizationId, this.projectId, documentId);
	}

	private DocumentResponse response(UUID id, DocumentStatus status, int chunkCount) {
		return new DocumentResponse(id, this.organizationId, this.projectId, "policy", "policy.txt", "text/plain", 42L,
				status, chunkCount, null, Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

}
