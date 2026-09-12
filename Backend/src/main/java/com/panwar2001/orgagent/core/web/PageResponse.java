package com.panwar2001.orgagent.core.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Stable paged response shape.
 *
 * <p>Spring's {@code Page} serialisation is explicitly not a stable JSON contract, so pages are
 * mapped onto this record at the edge of the API.
 *
 * @param content the page's items
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total number of matching items
 * @param totalPages total number of pages
 * @param <T> item type
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
