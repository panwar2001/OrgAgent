package com.panwar2001.orgagent.features.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.features.organization.Organization;
import com.panwar2001.orgagent.features.organization.OrganizationStatus;

/**
 * An organization as returned by the API.
 *
 * @param id organization identifier
 * @param name display name
 * @param slug URL-safe identifier
 * @param status lifecycle status
 * @param createdAt creation timestamp
 * @param updatedAt last modification timestamp
 */
public record OrganizationResponse(UUID id, String name, String slug, OrganizationStatus status, Instant createdAt,
		Instant updatedAt) {

	public static OrganizationResponse from(Organization organization) {
		return new OrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(),
				organization.getStatus(), organization.getCreatedAt(), organization.getUpdatedAt());
	}

}
