package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals a well-formed request whose content is semantically invalid. */
public class BadRequestException extends ApiException {

	public BadRequestException(String message) {
		super(requireStatus(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST), message);
	}

	public BadRequestException(ErrorCode code, String message) {
		super(requireStatus(code, HttpStatus.BAD_REQUEST), message);
	}

	public BadRequestException(ErrorCode code, String message, Throwable cause) {
		super(requireStatus(code, HttpStatus.BAD_REQUEST), message, cause);
	}

}
