package com.panwar2001.orgagent.features.ingestion.extract;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Reads markdown through Spring AI's reader so headings, code blocks and quotes are understood as
 * structure, then flattens the sections back into text for chunking.
 */
@Component
@Order(10)
public class MarkdownTextExtractor implements DocumentTextExtractor {

	private static final MarkdownDocumentReaderConfig CONFIG = MarkdownDocumentReaderConfig.builder()
		.withHorizontalRuleCreateDocument(false)
		.withIncludeCodeBlock(true)
		.withIncludeBlockquote(true)
		.build();

	/** Metadata key the reader uses for a section's heading. */
	static final String HEADING_METADATA_KEY = "title";

	@Override
	public boolean supports(MediaType mediaType, String fileName) {
		if (mediaType != null && "markdown".equals(mediaType.getSubtype())) {
			return true;
		}
		String extension = PlainTextExtractor.extension(fileName);
		return "md".equals(extension) || "markdown".equals(extension);
	}

	@Override
	public String extract(byte[] content, String fileName) {
		List<Document> sections = new MarkdownDocumentReader(new UploadedFileResource(content, fileName), CONFIG).get();
		return sections.stream()
			.map(MarkdownTextExtractor::withHeading)
			.filter(section -> !section.isBlank())
			.reduce((left, right) -> left + "\n\n" + right)
			.orElse("");
	}

	/**
	 * The reader returns each section's body and keeps its heading in the metadata. Headings carry
	 * meaning for retrieval ("Exceptions", "Refund window"), so they are put back in front of the body.
	 */
	private static String withHeading(Document section) {
		String body = section.getText() == null ? "" : section.getText().strip();
		Object heading = section.getMetadata().get(HEADING_METADATA_KEY);
		if (heading == null || String.valueOf(heading).isBlank() || body.startsWith(String.valueOf(heading))) {
			return body;
		}
		return String.valueOf(heading).strip() + "\n\n" + body;
	}

}
