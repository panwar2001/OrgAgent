package com.panwar2001.orgagent.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugifierTest {

	@ParameterizedTest
	@CsvSource(
			delimiter = '|',
			textBlock = """
					Acme Ltd.                | acme-ltd
					Human Resources (2026)   | human-resources-2026
					Acme Ltd. — HR (2026)    | acme-ltd-hr-2026
					  spaced   out  name     | spaced-out-name
					ACME                     | acme
					already-a-slug           | already-a-slug
					R&D / Product            | r-d-product
					""")
	void turnsNamesIntoUrlSafeSlugs(String name, String expected) {
		assertThat(Slugifier.slugify(name)).isEqualTo(expected);
	}

	@Test
	void collapsesRunsOfSeparatorsIntoASingleDash() {
		assertThat(Slugifier.slugify("a---b___c   d")).isEqualTo("a-b-c-d");
	}

	@Test
	void dropsLeadingAndTrailingSeparators() {
		assertThat(Slugifier.slugify("---hello---")).isEqualTo("hello");
	}

	@Test
	void truncatesToTheColumnWidth() {
		assertThat(Slugifier.slugify("a".repeat(200))).hasSize(120);
	}

	@Test
	void doesNotEndOnADashAfterTruncating() {
		String awkward = "a".repeat(119) + " b";

		assertThat(Slugifier.slugify(awkward)).hasSize(119).doesNotEndWith("-");
	}

	@Test
	void rejectsNamesWithNothingSluggableInThem() {
		assertThatThrownBy(() -> Slugifier.slugify("!!! ???")).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("!!! ???")
			.satisfies(ex -> assertThat(((BadRequestException) ex).code()).isEqualTo(ErrorCode.INVALID_PARAMETER));
	}

	@Test
	void rejectsANullName() {
		assertThatThrownBy(() -> Slugifier.slugify(null)).isInstanceOf(BadRequestException.class);
	}

	@Test
	void recognisesValuesThatAlreadyAreSlugs() {
		assertThat(Slugifier.isSlug("acme-hr")).isTrue();
		assertThat(Slugifier.isSlug("Acme HR")).isFalse();
		assertThat(Slugifier.isSlug("")).isFalse();
		assertThat(Slugifier.isSlug(null)).isFalse();
	}

}
