package com.panwar2001.orgagent.features.organization;

import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.support.Slugifier;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.organization.dto.CreateOrganizationRequest;
import com.panwar2001.orgagent.features.organization.dto.OrganizationResponse;
import com.panwar2001.orgagent.features.organization.dto.UpdateOrganizationRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases around organization accounts. */
@Service
public class OrganizationService {

	private final OrganizationRepository repository;

	public OrganizationService(OrganizationRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public OrganizationResponse create(CreateOrganizationRequest request) {
		String slug = requestedSlugOrDerived(request);
		if (this.repository.existsBySlug(slug)) {
			throw new ConflictException(ErrorCode.ORGANIZATION_ALREADY_EXISTS,
					"An organization with slug '%s' already exists".formatted(slug));
		}
		Organization saved = this.repository.save(Organization.create(request.name(), slug));
		return OrganizationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public OrganizationResponse get(UUID organizationId) {
		return OrganizationResponse.from(require(organizationId));
	}

	@Transactional(readOnly = true)
	public PageResponse<OrganizationResponse> list(Pageable pageable) {
		Page<Organization> page = this.repository.findAll(pageable);
		return PageResponse.from(page, OrganizationResponse::from);
	}

	@Transactional
	public OrganizationResponse rename(UUID organizationId, UpdateOrganizationRequest request) {
		Organization organization = require(organizationId);
		organization.rename(request.name());
		return OrganizationResponse.from(organization);
	}

	@Transactional
	public OrganizationResponse suspend(UUID organizationId) {
		Organization organization = require(organizationId);
		organization.suspend();
		return OrganizationResponse.from(organization);
	}

	@Transactional
	public OrganizationResponse activate(UUID organizationId) {
		Organization organization = require(organizationId);
		organization.activate();
		return OrganizationResponse.from(organization);
	}

	@Transactional
	public void delete(UUID organizationId) {
		this.repository.delete(require(organizationId));
	}

	/**
	 * Loads an organization or fails with a 404.
	 *
	 * <p>Public because other features (projects, ingestion, chat) must not be able to operate on an
	 * organization that does not exist.
	 */
	@Transactional(readOnly = true)
	public Organization require(UUID organizationId) {
		return this.repository.findById(organizationId)
			.orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.ORGANIZATION_NOT_FOUND, organizationId));
	}

	private String requestedSlugOrDerived(CreateOrganizationRequest request) {
		String requested = request.slug();
		return requested == null || requested.isBlank() ? Slugifier.slugify(request.name()) : Slugifier.slugify(requested);
	}

}
