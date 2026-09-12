package com.panwar2001.orgagent.features.ingestion.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.UnsupportedMediaTypeException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DocumentTextExtractorsTest {

	private final DocumentTextExtractors extractors = new DocumentTextExtractors(
			List.of(new MarkdownTextExtractor(), new PdfTextExtractor(), new PlainTextExtractor()));

	@Test
	void readsPlainText() {
		String text = extractors.extract(MediaType.TEXT_PLAIN, "notes.txt", "hello\nworld".getBytes(StandardCharsets.UTF_8));

		assertThat(text).isEqualTo("hello\nworld");
	}

	@Test
	void fallsBackToTheFileExtensionWhenTheMediaTypeIsUnknown() {
		String text = extractors.extract(MediaType.APPLICATION_OCTET_STREAM, "notes.log", "line".getBytes());

		assertThat(text).isEqualTo("line");
	}

	@Test
	void readsMarkdownThroughItsStructuredReader() {
		String markdown = """
				# Refund policy

				Refunds take five working days.

				## Exceptions

				Gift cards are not refundable.
				""";

		String text = extractors.extract(MediaType.parseMediaType("text/markdown"), "policy.md",
				markdown.getBytes(StandardCharsets.UTF_8));

		assertThat(text).contains("Refund policy").contains("five working days").contains("Gift cards");
	}

	@Test
	void routesPdfFilesToThePdfReader() {
		assertThat(extractors.supports(MediaType.APPLICATION_PDF, "handbook.pdf")).isTrue();
		assertThat(extractors.supports(MediaType.APPLICATION_OCTET_STREAM, "handbook.pdf")).isTrue();
	}

	@Test
	void routesMarkdownBeforeTheCatchAllTextReader() {
		assertThat(extractors.supports(MediaType.TEXT_PLAIN, "policy.md")).isTrue();
		assertThat(extractors.supports(MediaType.parseMediaType("text/markdown"), "policy")).isTrue();
	}

	@Test
	void rejectsFormatsItCannotRead() {
		assertThat(extractors.supports(MediaType.APPLICATION_OCTET_STREAM, "archive.zip")).isFalse();

		assertThatThrownBy(() -> extractors.extract(MediaType.APPLICATION_OCTET_STREAM, "archive.zip", new byte[0]))
			.isInstanceOf(UnsupportedMediaTypeException.class)
			.hasMessageContaining("archive.zip")
			.satisfies(ex -> assertThat(((UnsupportedMediaTypeException) ex).code())
				.isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
	}

}
