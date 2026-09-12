package com.panwar2001.orgagent.features.ingestion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	Optional<Document> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

	Page<Document> findByOrganizationIdAndProjectId(UUID organizationId, UUID projectId, Pageable pageable);

	Page<Document> findByOrganizationIdAndProjectIdAndStatus(UUID organizationId, UUID projectId, DocumentStatus status,
			Pageable pageable);

	boolean existsByProjectIdAndContentHash(UUID projectId, String contentHash);

	long countByProjectId(UUID projectId);

}
