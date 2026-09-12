package com.panwar2001.orgagent.features.project;

import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.support.Slugifier;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.organization.OrganizationService;
import com.panwar2001.orgagent.features.project.dto.CreateProjectRequest;
import com.panwar2001.orgagent.features.project.dto.ProjectResponse;
import com.panwar2001.orgagent.features.project.dto.UpdateProjectRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases around projects.
 *
 * <p>Every method takes the organization id and scopes its query by it, so a project of another
 * tenant is indistinguishable from one that does not exist.
 */
@Service
public class ProjectService {

	private final ProjectRepository repository;

	private final OrganizationService organizationService;

	public ProjectService(ProjectRepository repository, OrganizationService organizationService) {
		this.repository = repository;
		this.organizationService = organizationService;
	}

	@Transactional
	public ProjectResponse create(UUID organizationId, CreateProjectRequest request) {
		this.organizationService.require(organizationId);

		String slug = requestedSlugOrDerived(request);
		if (this.repository.existsByOrganizationIdAndSlug(organizationId, slug)) {
			throw new ConflictException(ErrorCode.PROJECT_ALREADY_EXISTS,
					"A project with slug '%s' already exists in this organization".formatted(slug));
		}

		Project saved = this.repository
			.save(Project.create(organizationId, request.name(), slug, request.description()));
		return ProjectResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(UUID organizationId, UUID projectId) {
		return ProjectResponse.from(require(organizationId, projectId));
	}

	@Transactional(readOnly = true)
	public PageResponse<ProjectResponse> list(UUID organizationId, Pageable pageable) {
		this.organizationService.require(organizationId);
		Page<Project> page = this.repository.findByOrganizationId(organizationId, pageable);
		return PageResponse.from(page, ProjectResponse::from);
	}

	@Transactional
	public ProjectResponse update(UUID organizationId, UUID projectId, UpdateProjectRequest request) {
		Project project = require(organizationId, projectId);
		if (request.name() != null && !request.name().isBlank()) {
			project.rename(request.name());
		}
		if (request.description() != null) {
			project.describe(request.description());
		}
		return ProjectResponse.from(project);
	}

	@Transactional
	public ProjectResponse archive(UUID organizationId, UUID projectId) {
		Project project = require(organizationId, projectId);
		project.archive();
		return ProjectResponse.from(project);
	}

	@Transactional
	public ProjectResponse activate(UUID organizationId, UUID projectId) {
		Project project = require(organizationId, projectId);
		project.activate();
		return ProjectResponse.from(project);
	}

	@Transactional
	public void delete(UUID organizationId, UUID projectId) {
		this.repository.delete(require(organizationId, projectId));
	}

	/** Number of projects an organization owns, used by organization-level reporting. */
	@Transactional(readOnly = true)
	public long count(UUID organizationId) {
		return this.repository.countByOrganizationId(organizationId);
	}

	/**
	 * Loads a project or fails with a 404.
	 *
	 * <p>Public so ingestion and chat can refuse to work on a project that does not exist.
	 */
	@Transactional(readOnly = true)
	public Project require(UUID organizationId, UUID projectId) {
		return this.repository.findByIdAndOrganizationId(projectId, organizationId)
			.orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.PROJECT_NOT_FOUND, projectId));
	}

	private String requestedSlugOrDerived(CreateProjectRequest request) {
		String requested = request.slug();
		return requested == null || requested.isBlank() ? Slugifier.slugify(request.name()) : Slugifier.slugify(requested);
	}

}
