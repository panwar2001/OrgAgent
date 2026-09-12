package com.panwar2001.orgagent.features.ingestion;

import java.util.ArrayList;
import java.util.List;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;

import org.springframework.stereotype.Component;

/**
 * Splits extracted document text into overlapping chunks.
 *
 * <p>Retrieval quality is decided here: a chunk must be small enough that its embedding is about one
 * topic, and consecutive chunks must overlap so a sentence spanning a boundary is still retrievable
 * from at least one of them.
 *
 * <p>Spring AI's {@code TokenTextSplitter} is not used because it cannot overlap chunks. This splitter
 * prefers paragraph boundaries, hard-cuts a paragraph that is longer than a whole chunk, and never
 * produces a chunk longer than the configured size.
 */
@Component
public class TextChunker {

	private static final String PARAGRAPH_SEPARATOR = "\n\n";

	private final int chunkSize;

	private final int overlap;

	public TextChunker(OrgAgentProperties properties) {
		OrgAgentProperties.Ingestion ingestion = properties.ingestion();
		this.chunkSize = ingestion.chunkSize();
		this.overlap = Math.min(ingestion.chunkOverlap(), ingestion.chunkSize() - 1);
	}

	/** The configured maximum size of one chunk, in characters. */
	public int chunkSize() {
		return this.chunkSize;
	}

	/**
	 * @param text the extracted document text
	 * @return chunks in reading order; empty when the text has no readable content
	 */
	public List<String> chunk(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String paragraph : text.split("\\R\\s*\\R")) {
			String block = paragraph.strip();
			if (block.isEmpty()) {
				continue;
			}

			for (String piece : splitOversized(block)) {
				if (current.isEmpty()) {
					current.append(piece);
				}
				else if (current.length() + PARAGRAPH_SEPARATOR.length() + piece.length() <= this.chunkSize) {
					current.append(PARAGRAPH_SEPARATOR).append(piece);
				}
				else {
					String previous = current.toString();
					chunks.add(previous);
					current.setLength(0);
					String overlapText = overlapTail(previous, piece);
					if (!overlapText.isEmpty()) {
						current.append(overlapText).append(PARAGRAPH_SEPARATOR);
					}
					current.append(piece);
				}
			}
		}

		if (!current.isEmpty()) {
			chunks.add(current.toString().strip());
		}
		return List.copyOf(chunks);
	}

	/** A paragraph longer than a whole chunk is cut on the chunk size. */
	private List<String> splitOversized(String block) {
		if (block.length() <= this.chunkSize) {
			return List.of(block);
		}

		List<String> pieces = new ArrayList<>();
		int start = 0;
		while (start < block.length()) {
			int end = Math.min(start + this.chunkSize, block.length());
			pieces.add(block.substring(start, end).strip());
			start = end;
		}
		return pieces;
	}

	/**
	 * The tail of the previous chunk that is repeated at the start of the next one, shortened when
	 * the following piece leaves no room for the full overlap.
	 */
	private String overlapTail(String previous, String next) {
		if (this.overlap <= 0 || previous.isEmpty()) {
			return "";
		}
		int room = this.chunkSize - next.length() - PARAGRAPH_SEPARATOR.length();
		if (room <= 0) {
			return "";
		}
		int length = Math.min(this.overlap, Math.min(room, previous.length()));
		return previous.substring(previous.length() - length).strip();
	}

}
