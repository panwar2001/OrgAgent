package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals that a requested entity does not exist (or is not visible to the caller). */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException(ErrorCode code, String message) {
		super(requireStatus(code, HttpStatus.NOT_FOUND), message);
	}

	public static ResourceNotFoundException of(ErrorCode code, Object identifier) {
		return new ResourceNotFoundException(code, "%s: %s".formatted(code.defaultMessage(), identifier));
	}

}
