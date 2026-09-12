package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every failure this application raises on purpose.
 *
 * <p>Carrying an {@link ErrorCode} instead of a bare message keeps the HTTP status and the
 * machine-readable code in one place, so {@link GlobalRestExceptionHandler} stays a thin mapper.
 */
public class ApiException extends RuntimeException {

	private final transient ErrorCode code;

	public ApiException(ErrorCode code) {
		this(code, code.defaultMessage(), null);
	}

	public ApiException(ErrorCode code, String message) {
		this(code, message, null);
	}

	public ApiException(ErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public ErrorCode code() {
		return this.code;
	}

	public HttpStatus status() {
		return this.code.status();
	}

	/**
	 * Guards subclasses against a mismatched code, e.g. a "not found" exception carrying a 409.
	 */
	static ErrorCode requireStatus(ErrorCode code, HttpStatus expected) {
		if (code.status() != expected) {
			throw new IllegalArgumentException(
					"ErrorCode %s maps to %s but %s was expected".formatted(code, code.status(), expected));
		}
		return code;
	}

}
