package com.panwar2001.orgagent.features.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.ingestion.Document;
import com.panwar2001.orgagent.features.ingestion.DocumentStatus;

/**
 * An ingested document as returned by the API.
 *
 * @param id document identifier
 * @param organizationId owning organization
 * @param projectId project the document was ingested into
 * @param title document title
 * @param fileName original file name
 * @param contentType detected media type
 * @param sizeBytes size of the uploaded file
 * @param status ingestion status
 * @param chunkCount number of chunks embedded for this document
 * @param errorMessage failure reason, present only for failed documents
 * @param createdAt creation timestamp
 * @param updatedAt last modification timestamp
 */
public record DocumentResponse(UUID id, UUID organizationId, UUID projectId, String title, String fileName,
		String contentType, long sizeBytes, DocumentStatus status, int chunkCount, String errorMessage,
		Instant createdAt, Instant updatedAt) {

	public static DocumentResponse from(Document document) {
		return new DocumentResponse(document.getId(), document.getOrganizationId(), document.getProjectId(),
				document.getTitle(), document.getFileName(), document.getContentType(), document.getSizeBytes(),
				document.getStatus(), document.getChunkCount(), document.getErrorMessage(), document.getCreatedAt(),
				document.getUpdatedAt());
	}

}
