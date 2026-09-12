package com.panwar2001.orgagent.features.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates an organization.
 *
 * @param name display name; a slug is derived from it when none is given
 * @param slug optional URL-safe identifier, must already be a valid slug if supplied
 */
public record CreateOrganizationRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 120) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "must be lowercase letters, digits and single dashes") String slug) {
}
