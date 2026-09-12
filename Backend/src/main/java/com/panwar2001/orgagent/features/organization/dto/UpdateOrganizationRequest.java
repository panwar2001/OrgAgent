package com.panwar2001.orgagent.features.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Renames an organization. Slugs are deliberately immutable for now: changing one would break every
 * link and stored reference pointing at it.
 *
 * @param name the new display name
 */
public record UpdateOrganizationRequest(@NotBlank @Size(max = 200) String name) {
}
