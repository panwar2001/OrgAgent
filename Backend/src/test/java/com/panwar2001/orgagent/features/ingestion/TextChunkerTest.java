package com.panwar2001.orgagent.features.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class TextChunkerTest {

	private final TextChunker chunker = chunker(100, 20);

	@Test
	void returnsNothingForEmptyContent() {
		assertThat(chunker.chunk(null)).isEmpty();
		assertThat(chunker.chunk("")).isEmpty();
		assertThat(chunker.chunk("   \n\n  ")).isEmpty();
	}

	@Test
	void keepsAShortDocumentInOneChunk() {
		List<String> chunks = chunker.chunk("Refunds are processed within five working days.");

		assertThat(chunks).containsExactly("Refunds are processed within five working days.");
	}

	@Test
	void joinsParagraphsThatStillFit() {
		List<String> chunks = chunker.chunk("First paragraph.\n\nSecond paragraph.");

		assertThat(chunks).containsExactly("First paragraph.\n\nSecond paragraph.");
	}

	@Test
	void startsANewChunkWhenTheNextParagraphDoesNotFit() {
		List<String> chunks = chunker.chunk("A".repeat(60) + "\n\n" + "B".repeat(60));

		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0)).isEqualTo("A".repeat(60));
		assertThat(chunks.get(1)).endsWith("B".repeat(60));
	}

	@Test
	void repeatsTheTailOfThePreviousChunkSoBoundariesStayRetrievable() {
		List<String> chunks = chunker.chunk("A".repeat(60) + "\n\n" + "B".repeat(60));

		// The configured overlap is 20 characters, and the 60-character paragraph leaves room for it.
		assertThat(chunks.get(1)).startsWith("A".repeat(20));
	}

	@Test
	void neverProducesAChunkLongerThanTheConfiguredSize() {
		String text = ("Paragraph one is reasonably long. ".repeat(4) + "\n\n"
				+ "Paragraph two is reasonably long. ".repeat(4) + "\n\n"
				+ "Paragraph three is reasonably long. ".repeat(4));

		List<String> chunks = chunker.chunk(text);

		assertThat(chunks).isNotEmpty().allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
	}

	@Test
	void shrinksTheOverlapWhenTheNextParagraphLeavesNoRoom() {
		List<String> chunks = chunker.chunk("A".repeat(20) + "\n\n" + "B".repeat(95));

		assertThat(chunks).hasSize(2).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
	}

	@Test
	void hardCutsAParagraphThatIsLongerThanAWholeChunk() {
		List<String> chunks = chunker.chunk("C".repeat(250));

		assertThat(chunks).hasSize(3).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
		assertThat(chunks.get(0)).isEqualTo("C".repeat(100));
		assertThat(chunks.get(1)).isEqualTo("C".repeat(100));
	}

	@Test
	void doesNotOverlapWhenOverlapIsDisabled() {
		List<String> chunks = chunker(50, 0).chunk("A".repeat(40) + "\n\n" + "B".repeat(40));

		assertThat(chunks).containsExactly("A".repeat(40), "B".repeat(40));
	}

	@Test
	void toleratesAnOverlapLargerThanTheChunkSize() {
		TextChunker extreme = chunker(50, 500);

		List<String> chunks = extreme.chunk("A".repeat(40) + "\n\n" + "B".repeat(40));

		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(50));
	}

	@Test
	void reportsTheConfiguredChunkSize() {
		assertThat(chunker.chunkSize()).isEqualTo(100);
	}

	@Test
	void ignoresBlankParagraphsBetweenContent() {
		List<String> chunks = chunker.chunk("First.\n\n\n\n   \n\nSecond.");

		assertThat(chunks).containsExactly("First.\n\nSecond.");
	}

	private TextChunker chunker(int chunkSize, int overlap) {
		OrgAgentProperties defaults = TestProperties.defaults();
		return new TextChunker(new OrgAgentProperties(defaults.rag(), defaults.cache(),
				new OrgAgentProperties.Ingestion(DataSize.ofMegabytes(1), chunkSize, overlap), defaults.cors()));
	}

}
