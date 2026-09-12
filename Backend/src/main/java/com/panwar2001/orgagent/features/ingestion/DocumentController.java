package com.panwar2001.orgagent.features.ingestion;

import java.util.UUID;

import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.core.web.Pagination;
import com.panwar2001.orgagent.features.ingestion.dto.DocumentResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/documents")
@Tag(name = "Documents", description = "Ingest and inspect project documents")
public class DocumentController {

	private final IngestionService service;

	public DocumentController(IngestionService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Upload a document, embed it and make it searchable")
	public DocumentResponse upload(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@RequestPart("file") MultipartFile file) {
		return this.service.ingest(organizationId, projectId, file);
	}

	@GetMapping
	@Operation(summary = "List the documents of a project")
	public PageResponse<DocumentResponse> list(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return this.service.list(organizationId, projectId, Pagination.of(page, size, "createdAt"));
	}

	@GetMapping("/{documentId}")
	@Operation(summary = "Read one document and its ingestion status")
	public DocumentResponse get(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@PathVariable UUID documentId) {
		return this.service.get(organizationId, projectId, documentId);
	}

	@DeleteMapping("/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a document and its embeddings")
	public void delete(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@PathVariable UUID documentId) {
		this.service.delete(organizationId, projectId, documentId);
	}

}
