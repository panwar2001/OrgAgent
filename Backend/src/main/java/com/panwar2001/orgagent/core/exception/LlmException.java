package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the language model (or its transport) failed for reasons outside this service.
 * The HTTP status is a 502, so callers can tell an upstream failure from a bug of ours.
 */
public class LlmException extends ApiException {

	public LlmException(String message, Throwable cause) {
		super(requireStatus(ErrorCode.LLM_FAILED, HttpStatus.BAD_GATEWAY), message, cause);
	}

	public LlmException(ErrorCode code, String message, Throwable cause) {
		super(requireStatus(code, HttpStatus.BAD_GATEWAY), message, cause);
	}

}
