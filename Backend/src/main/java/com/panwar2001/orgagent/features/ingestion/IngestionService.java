package com.panwar2001.orgagent.features.ingestion;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.exception.ApiException;
import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.IngestionException;
import com.panwar2001.orgagent.core.exception.PayloadTooLargeException;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.ingestion.dto.DocumentResponse;
import com.panwar2001.orgagent.features.ingestion.extract.DocumentTextExtractors;
import com.panwar2001.orgagent.features.project.Project;
import com.panwar2001.orgagent.features.project.ProjectService;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns an uploaded file into searchable vectors.
 *
 * <p>The pipeline is deliberately not one long transaction. The document row is committed as
 * {@code PENDING} first, then the slow external work happens (text extraction, embedding through the
 * model provider), and only then is the row marked {@code INDEXED}. A failure halfway therefore
 * leaves a {@code FAILED} row with the reason attached instead of vanishing in a rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

	/** Metadata keys attached to every embedded chunk; retrieval filters on them. */
	static final String META_ORGANIZATION_ID = "organizationId";

	static final String META_PROJECT_ID = "projectId";

	static final String META_DOCUMENT_ID = "documentId";

	static final String META_TITLE = "title";

	static final String META_CHUNK_INDEX = "chunkIndex";

	static final String META_PROJECT_SLUG = "projectSlug";

	private final DocumentRepository repository;

	private final ProjectService projectService;

	private final DocumentTextExtractors extractors;

	private final TextChunker chunker;

	private final VectorStore vectorStore;

	private final OrgAgentProperties properties;

	/**
	 * Ingests one file into a project.
	 *
	 * @param organizationId owning organization
	 * @param projectId target project, which must exist and be active
	 * @param file the uploaded file
	 * @return the stored document, already indexed
	 */
	public DocumentResponse ingest(UUID organizationId, UUID projectId, MultipartFile file) {
		Project project = requireWritableProject(organizationId, projectId);
		byte[] content = read(file);
		rejectOversizedFile(file, content);

		MediaType mediaType = mediaTypeOf(file);
		String contentHash = sha256(content);
		if (this.repository.existsByProjectIdAndContentHash(projectId, contentHash)) {
			throw new ConflictException(ErrorCode.DOCUMENT_ALREADY_EXISTS,
					"An identical file has already been ingested into this project");
		}

		Document pending = this.repository.save(Document.pending(organizationId, projectId, titleOf(file),
				fileNameOf(file), mediaType.toString(), content.length, contentHash));

		try {
			String text = this.extractors.extract(mediaType, fileNameOf(file), content);
			List<String> chunks = this.chunker.chunk(text);
			if (chunks.isEmpty()) {
				throw new BadRequestException(ErrorCode.INVALID_REQUEST,
						"No readable text could be extracted from '%s'".formatted(fileNameOf(file)));
			}

			this.vectorStore.add(toVectorDocuments(pending, chunks, project));

			pending.markIndexed(chunks.size());
			Document indexed = this.repository.save(pending);
			log.info("Indexed {} chunk(s) of document {} into project {}", chunks.size(), indexed.getId(), projectId);
			return DocumentResponse.from(indexed);
		}
		catch (ApiException failure) {
			markFailed(pending, failure);
			throw failure;
		}
		catch (RuntimeException failure) {
			markFailed(pending, failure);
			throw new IngestionException("Could not ingest '%s'".formatted(fileNameOf(file)), failure);
		}
	}

	/** Documents of one project, newest first if the caller asks for it. */
	public PageResponse<DocumentResponse> list(UUID organizationId, UUID projectId, Pageable pageable) {
		this.projectService.require(organizationId, projectId);
		Page<Document> page = this.repository.findByOrganizationIdAndProjectId(organizationId, projectId, pageable);
		return PageResponse.from(page, DocumentResponse::from);
	}

	public DocumentResponse get(UUID organizationId, UUID projectId, UUID documentId) {
		return DocumentResponse.from(require(organizationId, projectId, documentId));
	}

	/** Removes a document together with every vector embedded from it. */
	public void delete(UUID organizationId, UUID projectId, UUID documentId) {
		Document document = require(organizationId, projectId, documentId);
		this.vectorStore.delete(new FilterExpressionBuilder().eq(META_DOCUMENT_ID, documentId.toString()).build());
		this.repository.delete(document);
		log.info("Deleted document {} and its embeddings from project {}", documentId, projectId);
	}

	/** Loads a document or fails with a 404, scoped by organization and project. */
	public Document require(UUID organizationId, UUID projectId, UUID documentId) {
		return this.repository.findByIdAndOrganizationIdAndProjectId(documentId, organizationId, projectId)
			.orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.DOCUMENT_NOT_FOUND, documentId));
	}

	private List<org.springframework.ai.document.Document> toVectorDocuments(Document stored, List<String> chunks,
			Project project) {
		List<org.springframework.ai.document.Document> documents = new ArrayList<>(chunks.size());
		for (int index = 0; index < chunks.size(); index++) {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put(META_ORGANIZATION_ID, stored.getOrganizationId().toString());
			metadata.put(META_PROJECT_ID, stored.getProjectId().toString());
			metadata.put(META_DOCUMENT_ID, stored.getId().toString());
			metadata.put(META_TITLE, stored.getTitle());
			metadata.put(META_CHUNK_INDEX, index);
			metadata.put(META_PROJECT_SLUG, project.getSlug());
			documents.add(new org.springframework.ai.document.Document(UUID.randomUUID().toString(), chunks.get(index),
					metadata));
		}
		return documents;
	}

	private Project requireWritableProject(UUID organizationId, UUID projectId) {
		Project project = this.projectService.require(organizationId, projectId);
		if (!project.isActive()) {
			throw new ConflictException(ErrorCode.CONFLICT,
					"Project '%s' is archived and no longer accepts documents".formatted(project.getSlug()));
		}
		return project;
	}

	private void rejectOversizedFile(MultipartFile file, byte[] content) {
		DataSize max = this.properties.ingestion().maxFileSize();
		if (content.length > max.toBytes()) {
			throw new PayloadTooLargeException(
					"'%s' is %d bytes; the limit is %s".formatted(fileNameOf(file), content.length, max));
		}
	}

	private byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException(ErrorCode.INVALID_REQUEST, "An uploaded file is required");
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new IngestionException("Uploaded file could not be read", ex);
		}
	}

	private void markFailed(Document document, RuntimeException failure) {
		log.warn("Ingestion of document {} failed: {}", document.getId(), failure.getMessage());
		document.markFailed(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
		try {
			this.repository.save(document);
		}
		catch (RuntimeException saveFailure) {
			log.error("Could not record the ingestion failure of document {}", document.getId(), saveFailure);
		}
	}

	private MediaType mediaTypeOf(MultipartFile file) {
		String declared = file.getContentType();
		if (declared == null || declared.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(declared);
		}
		catch (IllegalArgumentException ex) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	private String titleOf(MultipartFile file) {
		String name = fileNameOf(file);
		int dot = name.lastIndexOf('.');
		String title = dot > 0 ? name.substring(0, dot) : name;
		return title.length() <= 300 ? title : title.substring(0, 300);
	}

	private String fileNameOf(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "upload";
		}
		String withoutPath = name.replace('\\', '/');
		String base = withoutPath.substring(withoutPath.lastIndexOf('/') + 1);
		return base.length() <= 300 ? base : base.substring(base.length() - 300);
	}

	static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
		}
	}

}
