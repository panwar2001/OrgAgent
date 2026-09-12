package com.panwar2001.orgagent.features.project;

/** Lifecycle of a project. */
public enum ProjectStatus {

	/** Accepts documents and answers questions. */
	ACTIVE,

	/** Read-only: no new ingestion, existing answers remain available. */
	ARCHIVED

}
