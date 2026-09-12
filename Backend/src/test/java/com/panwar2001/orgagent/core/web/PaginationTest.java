package com.panwar2001.orgagent.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationTest {

	@Test
	void buildsAnAscendingPageRequest() {
		Pageable pageable = Pagination.of(2, 50, "name");

		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getPageSize()).isEqualTo(50);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
	}

	@Test
	void acceptsTheBoundaryValues() {
		assertThat(Pagination.of(0, 1, "name").getPageSize()).isEqualTo(1);
		assertThat(Pagination.of(0, Pagination.MAX_PAGE_SIZE, "name").getPageSize())
			.isEqualTo(Pagination.MAX_PAGE_SIZE);
	}

	@Test
	void refusesANegativePageIndexInsteadOfThrowingLater() {
		assertThatThrownBy(() -> Pagination.of(-1, 20, "name")).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("page")
			.satisfies(ex -> assertThat(((BadRequestException) ex).code()).isEqualTo(ErrorCode.INVALID_PARAMETER));
	}

	@Test
	void refusesAPageSizeOfZeroOrLess() {
		assertThatThrownBy(() -> Pagination.of(0, 0, "name")).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("size");
	}

	@Test
	void refusesAPageSizeBeyondTheMaximum() {
		assertThatThrownBy(() -> Pagination.of(0, Pagination.MAX_PAGE_SIZE + 1, "name"))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("between 1 and 100");
	}

}
