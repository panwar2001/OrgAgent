package com.panwar2001.orgagent.core.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error payload shape produced by {@link GlobalRestExceptionHandler}.
 *
 * @param timestamp moment the error was produced (UTC)
 * @param status HTTP status code
 * @param code stable {@link ErrorCode} name clients can branch on
 * @param message human-readable explanation
 * @param path request URI that failed
 * @param violations per-field details, present only for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		List<FieldViolation> violations) {

	/** A single invalid field of an otherwise well-formed request. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FieldViolation(String field, String message, Object rejectedValue) {

		public static FieldViolation of(String field, String message, Object rejectedValue) {
			return new FieldViolation(field, message, rejectedValue);
		}

	}

	public static ApiError of(ErrorCode code, String message, String path) {
		return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, List.of());
	}

	public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> violations) {
		return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
				violations == null ? List.of() : List.copyOf(violations));
	}

	/** Escape hatch for statuses Spring raises itself, e.g. {@code ResponseStatusException}. */
	public static ApiError of(int status, String code, String message, String path) {
		return new ApiError(Instant.now(), status, code, message, path, List.of());
	}

}
