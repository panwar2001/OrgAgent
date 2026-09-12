package com.panwar2001.orgagent.features.project.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.project.Project;
import com.panwar2001.orgagent.features.project.ProjectStatus;

/**
 * A project as returned by the API.
 *
 * @param id project identifier
 * @param organizationId owning organization
 * @param name display name
 * @param slug URL-safe identifier, unique inside the organization
 * @param description optional description
 * @param status lifecycle status
 * @param createdAt creation timestamp
 * @param updatedAt last modification timestamp
 */
public record ProjectResponse(UUID id, UUID organizationId, String name, String slug, String description,
		ProjectStatus status, Instant createdAt, Instant updatedAt) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getId(), project.getOrganizationId(), project.getName(), project.getSlug(),
				project.getDescription(), project.getStatus(), project.getCreatedAt(), project.getUpdatedAt());
	}

}
