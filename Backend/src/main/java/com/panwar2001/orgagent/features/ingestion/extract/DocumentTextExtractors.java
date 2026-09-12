package com.panwar2001.orgagent.features.ingestion.extract;

import java.util.List;
import java.util.Optional;

import com.panwar2001.orgagent.core.exception.UnsupportedMediaTypeException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Picks the extractor for a file.
 *
 * <p>Extractors are consulted in order and the first one that recognises the media type — or, failing
 * that, the file extension — wins. An unrecognised file is rejected with a 415 and a message naming
 * the supported formats, rather than being stored and silently unsearchable.
 */
@Component
public class DocumentTextExtractors {

	private final List<DocumentTextExtractor> extractors;

	public DocumentTextExtractors(List<DocumentTextExtractor> extractors) {
		this.extractors = List.copyOf(extractors);
	}

	public String extract(MediaType mediaType, String fileName, byte[] content) {
		return extractorFor(mediaType, fileName)
			.orElseThrow(() -> new UnsupportedMediaTypeException(
					"Cannot read '%s' (%s); supported formats are PDF, markdown and plain text"
						.formatted(fileName, mediaType == null ? "unknown type" : mediaType)))
			.extract(content, fileName);
	}

	public boolean supports(MediaType mediaType, String fileName) {
		return extractorFor(mediaType, fileName).isPresent();
	}

	private Optional<DocumentTextExtractor> extractorFor(MediaType mediaType, String fileName) {
		return this.extractors.stream().filter(extractor -> extractor.supports(mediaType, fileName)).findFirst();
	}

}
