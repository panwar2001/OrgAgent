package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that an authenticated caller may not touch the requested resource.
 *
 * <p>This is what keeps one organization's projects invisible to another once authentication
 * is switched on; the API is currently open.
 */
public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(requireStatus(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN), message);
	}

}
