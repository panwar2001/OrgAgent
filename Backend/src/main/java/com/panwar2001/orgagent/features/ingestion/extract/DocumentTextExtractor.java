package com.panwar2001.orgagent.features.ingestion.extract;

import org.springframework.http.MediaType;

/**
 * Turns an uploaded file into plain text.
 *
 * <p>One implementation per family of formats keeps format-specific concerns (PDF layout, markdown
 * structure) out of the ingestion pipeline, which only ever sees text.
 */
public interface DocumentTextExtractor {

	/** True when this extractor can read the given media type or file name. */
	boolean supports(MediaType mediaType, String fileName);

	/** Extracts the readable text of the file. */
	String extract(byte[] content, String fileName);

}
