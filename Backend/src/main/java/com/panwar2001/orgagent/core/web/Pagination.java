package com.panwar2001.orgagent.core.web;

import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns the {@code page} / {@code size} query parameters into a {@link Pageable}.
 *
 * <p>Validated here rather than with {@code @Min}/{@code @Max} on the controller parameters: the
 * rules are then enforced identically however the controller is invoked, and an unusable page
 * request becomes the API's normal 400 instead of an unhandled {@link IllegalArgumentException}.
 */
public final class Pagination {

	public static final int DEFAULT_PAGE_SIZE = 20;

	public static final int MAX_PAGE_SIZE = 100;

	private Pagination() {
	}

	/**
	 * @param page zero-based page index
	 * @param size requested page size, capped at {@value #MAX_PAGE_SIZE}
	 * @param sortProperty entity property to sort ascending by
	 * @return a validated page request
	 * @throws BadRequestException if the page index or size is out of range
	 */
	public static Pageable of(int page, int size, String sortProperty) {
		if (page < 0) {
			throw new BadRequestException(ErrorCode.INVALID_PARAMETER,
					"'page' must be zero or greater but was %d".formatted(page));
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BadRequestException(ErrorCode.INVALID_PARAMETER,
					"'size' must be between 1 and %d but was %d".formatted(MAX_PAGE_SIZE, size));
		}
		return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortProperty));
	}

}
