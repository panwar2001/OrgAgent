package com.panwar2001.orgagent.features.project;

import java.util.UUID;

import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.core.web.Pagination;
import com.panwar2001.orgagent.features.project.dto.CreateProjectRequest;
import com.panwar2001.orgagent.features.project.dto.ProjectResponse;
import com.panwar2001.orgagent.features.project.dto.UpdateProjectRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Manage projects inside an organization")
public class ProjectController {

	private final ProjectService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a project in an organization")
	public ProjectResponse create(@PathVariable UUID organizationId,
			@Valid @RequestBody CreateProjectRequest request) {
		return this.service.create(organizationId, request);
	}

	@GetMapping
	@Operation(summary = "List the projects of an organization")
	public PageResponse<ProjectResponse> list(@PathVariable UUID organizationId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return this.service.list(organizationId, Pagination.of(page, size, "name"));
	}

	@GetMapping("/{projectId}")
	@Operation(summary = "Read one project")
	public ProjectResponse get(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
		return this.service.get(organizationId, projectId);
	}

	@PatchMapping("/{projectId}")
	@Operation(summary = "Update a project")
	public ProjectResponse update(@PathVariable UUID organizationId, @PathVariable UUID projectId,
			@Valid @RequestBody UpdateProjectRequest request) {
		return this.service.update(organizationId, projectId, request);
	}

	@PostMapping("/{projectId}/archive")
	@Operation(summary = "Archive a project so it stops accepting documents")
	public ProjectResponse archive(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
		return this.service.archive(organizationId, projectId);
	}

	@PostMapping("/{projectId}/activate")
	@Operation(summary = "Reactivate an archived project")
	public ProjectResponse activate(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
		return this.service.activate(organizationId, projectId);
	}

	@DeleteMapping("/{projectId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a project and everything it owns")
	public void delete(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
		this.service.delete(organizationId, projectId);
	}

}
