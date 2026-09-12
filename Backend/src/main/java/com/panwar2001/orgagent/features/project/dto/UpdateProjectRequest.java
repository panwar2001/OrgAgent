package com.panwar2001.orgagent.features.project.dto;

import jakarta.validation.constraints.Size;

/**
 * Updates a project. Every field is optional: an omitted or blank field keeps its current value.
 *
 * @param name new display name, if it should change
 * @param description new description, if it should change
 */
public record UpdateProjectRequest(@Size(max = 200) String name, @Size(max = 2000) String description) {

	/** True when the request carries nothing to change. */
	public boolean isEmpty() {
		return (name == null || name.isBlank()) && description == null;
	}

}
