package com.panwar2001.orgagent.features.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates a project inside an organization.
 *
 * @param name display name; a slug is derived from it when none is given
 * @param slug optional URL-safe identifier, unique inside the organization
 * @param description optional free-text purpose of the project
 */
public record CreateProjectRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 120) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "must be lowercase letters, digits and single dashes") String slug,
		@Size(max = 2000) String description) {
}
