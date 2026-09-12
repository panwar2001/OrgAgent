package com.panwar2001.orgagent.features.ingestion;

/** Where a document is in the ingestion pipeline. */
public enum DocumentStatus {

	/** Stored, but not searchable yet: extraction or embedding has not finished. */
	PENDING,

	/** Chunked, embedded and searchable. */
	INDEXED,

	/** Extraction or embedding failed; the reason is kept on the document. */
	FAILED

}
