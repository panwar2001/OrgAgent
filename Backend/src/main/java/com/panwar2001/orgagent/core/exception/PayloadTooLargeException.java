package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals that a payload is larger than the configured limit. */
public class PayloadTooLargeException extends ApiException {

	public PayloadTooLargeException(String message) {
		super(requireStatus(ErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE), message);
	}

}
