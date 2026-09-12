package com.panwar2001.orgagent.features.ingestion.extract;

import org.springframework.core.io.ByteArrayResource;

/**
 * An uploaded file as a Spring {@code Resource}.
 *
 * <p>Spring AI's readers use the resource file name for metadata and format detection, which a plain
 * {@code ByteArrayResource} does not carry.
 */
final class UploadedFileResource extends ByteArrayResource {

	private final String fileName;

	UploadedFileResource(byte[] content, String fileName) {
		super(content);
		this.fileName = fileName;
	}

	@Override
	public String getFilename() {
		return this.fileName;
	}

}
