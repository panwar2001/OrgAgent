package com.panwar2001.orgagent.features.ingestion.extract;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Reads PDFs page by page with Spring AI's PDF reader and flattens them into one text. */
@Component
@Order(10)
public class PdfTextExtractor implements DocumentTextExtractor {

	@Override
	public boolean supports(MediaType mediaType, String fileName) {
		if (mediaType != null && MediaType.APPLICATION_PDF.includes(mediaType)) {
			return true;
		}
		return "pdf".equals(PlainTextExtractor.extension(fileName));
	}

	@Override
	public String extract(byte[] content, String fileName) {
		List<Document> pages = new PagePdfDocumentReader(new UploadedFileResource(content, fileName)).get();
		return pages.stream().map(Document::getText).reduce((left, right) -> left + "\n\n" + right).orElse("");
	}

}
