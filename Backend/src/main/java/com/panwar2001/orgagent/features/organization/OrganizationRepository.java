package com.panwar2001.orgagent.features.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	Optional<Organization> findBySlug(String slug);

	boolean existsBySlug(String slug);

	Page<Organization> findByStatus(OrganizationStatus status, Pageable pageable);

}
