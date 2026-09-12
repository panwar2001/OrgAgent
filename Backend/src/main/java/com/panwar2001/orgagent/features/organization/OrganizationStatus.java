package com.panwar2001.orgagent.features.organization;

/** Lifecycle of an organization account. */
public enum OrganizationStatus {

	/** Normal, fully usable account. */
	ACTIVE,

	/** Temporarily blocked: the data stays, but the account should not be served. */
	SUSPENDED,

	/** Retired account kept for audit purposes. */
	ARCHIVED

}
