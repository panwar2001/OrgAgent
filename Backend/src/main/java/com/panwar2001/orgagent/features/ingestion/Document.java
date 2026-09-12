package com.panwar2001.orgagent.features.ingestion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One uploaded file and the state of its ingestion.
 *
 * <p>The document row is written before embedding starts and updated afterwards, so a crash mid
 * ingestion leaves a {@code PENDING} or {@code FAILED} record that can be seen and retried, rather
 * than a silent gap between "the user uploaded a file" and "the chatbot knows nothing about it".
 */
@Entity
@Table(name = "documents")
@Getter
public class Document {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "organization_id", nullable = false, updatable = false)
	private UUID organizationId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "file_name", nullable = false, length = 300)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 150)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	/** SHA-256 of the uploaded bytes, used to reject the same file ingested twice. */
	@Column(name = "content_hash", nullable = false, length = 64, updatable = false)
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private DocumentStatus status = DocumentStatus.PENDING;

	@Column(name = "chunk_count", nullable = false)
	private int chunkCount;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Document() {
		// for JPA
	}

	private Document(UUID id, UUID organizationId, UUID projectId, String title, String fileName, String contentType,
			long sizeBytes, String contentHash) {
		this.id = id;
		this.organizationId = organizationId;
		this.projectId = projectId;
		this.title = title;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.contentHash = contentHash;
	}

	public static Document pending(UUID organizationId, UUID projectId, String title, String fileName,
			String contentType, long sizeBytes, String contentHash) {
		return new Document(UUID.randomUUID(), Objects.requireNonNull(organizationId, "organizationId"),
				Objects.requireNonNull(projectId, "projectId"), requireText(title, "title"),
				requireText(fileName, "fileName"), requireText(contentType, "contentType"), sizeBytes,
				requireText(contentHash, "contentHash"));
	}

	/** Rehydrates a document, e.g. in tests. */
	public static Document of(UUID id, UUID organizationId, UUID projectId, String title, String fileName,
			String contentType, DocumentStatus status, int chunkCount) {
		Document document = new Document(Objects.requireNonNull(id, "id"), organizationId, projectId, title, fileName,
				contentType, 0L, "hash");
		document.status = Objects.requireNonNull(status, "status");
		document.chunkCount = chunkCount;
		return document;
	}

	/** Records a successful ingestion. */
	public void markIndexed(int chunks) {
		if (chunks < 1) {
			throw new IllegalArgumentException("An indexed document must have at least one chunk");
		}
		this.status = DocumentStatus.INDEXED;
		this.chunkCount = chunks;
		this.errorMessage = null;
	}

	/** Records a failed ingestion, keeping the reason for the operator. */
	public void markFailed(String reason) {
		this.status = DocumentStatus.FAILED;
		this.errorMessage = truncate(reason);
	}

	public boolean isIndexed() {
		return this.status == DocumentStatus.INDEXED;
	}

	public boolean belongsTo(UUID candidateOrganizationId, UUID candidateProjectId) {
		return this.organizationId.equals(candidateOrganizationId) && this.projectId.equals(candidateProjectId);
	}

	@PrePersist
	void onInsert() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	private static String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= 2000 ? value : value.substring(0, 2000);
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Document " + field + " must not be blank");
		}
		return value.trim();
	}

}
