package com.panwar2001.orgagent.features.ingestion.extract;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Reads formats that are already text: plain text, CSV, JSON, YAML and friends.
 *
 * <p>Ordered last: format-aware extractors (markdown, PDF) get the first look, and this one is the
 * catch-all for everything else that is textual.
 */
@Component
@Order(20)
public class PlainTextExtractor implements DocumentTextExtractor {

	private static final Set<String> EXTENSIONS = Set.of("txt", "text", "csv", "json", "yaml", "yml", "log");

	@Override
	public boolean supports(MediaType mediaType, String fileName) {
		if (mediaType != null && (MediaType.TEXT_PLAIN.includes(mediaType) || "csv".equals(mediaType.getSubtype())
				|| "json".equals(mediaType.getSubtype()) || "yaml".equals(mediaType.getSubtype()))) {
			return true;
		}
		return EXTENSIONS.contains(extension(fileName));
	}

	@Override
	public String extract(byte[] content, String fileName) {
		return new String(content, StandardCharsets.UTF_8);
	}

	static String extension(String fileName) {
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

}
