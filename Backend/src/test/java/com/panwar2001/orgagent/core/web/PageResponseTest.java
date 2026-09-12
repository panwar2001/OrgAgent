package com.panwar2001.orgagent.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void exposesThePageMetadataClientsNeed() {
		PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

		PageResponse<String> response = PageResponse.from(page, value -> value.toUpperCase());

		assertThat(response.content()).containsExactly("A", "B");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
	}

	@Test
	void mapsAnEmptyPageWithoutFailing() {
		PageResponse<String> response = PageResponse.from(new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0),
				value -> value);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
		assertThat(response.totalPages()).isZero();
	}

}
