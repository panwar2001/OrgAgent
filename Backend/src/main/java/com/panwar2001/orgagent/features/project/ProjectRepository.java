package com.panwar2001.orgagent.features.project;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);

	Page<Project> findByOrganizationId(UUID organizationId, Pageable pageable);

	Page<Project> findByOrganizationIdAndStatus(UUID organizationId, ProjectStatus status, Pageable pageable);

	boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);

	long countByOrganizationId(UUID organizationId);

}
