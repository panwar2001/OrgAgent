package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals that the request clashes with the current state of a resource (duplicate, stale write). */
public class ConflictException extends ApiException {

	public ConflictException(String message) {
		super(requireStatus(ErrorCode.CONFLICT, HttpStatus.CONFLICT), message);
	}

	public ConflictException(ErrorCode code, String message) {
		super(requireStatus(code, HttpStatus.CONFLICT), message);
	}

}
