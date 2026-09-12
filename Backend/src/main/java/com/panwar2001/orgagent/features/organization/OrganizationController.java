package com.panwar2001.orgagent.features.organization;

import java.util.UUID;

import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.core.web.Pagination;
import com.panwar2001.orgagent.features.organization.dto.CreateOrganizationRequest;
import com.panwar2001.orgagent.features.organization.dto.OrganizationResponse;
import com.panwar2001.orgagent.features.organization.dto.UpdateOrganizationRequest;

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

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Manage organization accounts")
public class OrganizationController {

	private final OrganizationService service;

	public OrganizationController(OrganizationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an organization account")
	public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
		return this.service.create(request);
	}

	@GetMapping
	@Operation(summary = "List organization accounts")
	public PageResponse<OrganizationResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.service.list(Pagination.of(page, size, "name"));
	}

	@GetMapping("/by-slug/{slug}")
	@Operation(summary = "Read one organization account by its slug")
	public OrganizationResponse getBySlug(@PathVariable String slug) {
		return this.service.getBySlug(slug);
	}

	@GetMapping("/{organizationId}")
	@Operation(summary = "Read one organization account")
	public OrganizationResponse get(@PathVariable UUID organizationId) {
		return this.service.get(organizationId);
	}

	@PatchMapping("/{organizationId}")
	@Operation(summary = "Rename an organization account")
	public OrganizationResponse rename(@PathVariable UUID organizationId,
			@Valid @RequestBody UpdateOrganizationRequest request) {
		return this.service.rename(organizationId, request);
	}

	@PostMapping("/{organizationId}/suspend")
	@Operation(summary = "Suspend an organization account")
	public OrganizationResponse suspend(@PathVariable UUID organizationId) {
		return this.service.suspend(organizationId);
	}

	@PostMapping("/{organizationId}/activate")
	@Operation(summary = "Reactivate a suspended organization account")
	public OrganizationResponse activate(@PathVariable UUID organizationId) {
		return this.service.activate(organizationId);
	}

	@DeleteMapping("/{organizationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete an organization account and everything it owns")
	public void delete(@PathVariable UUID organizationId) {
		this.service.delete(organizationId);
	}

}
