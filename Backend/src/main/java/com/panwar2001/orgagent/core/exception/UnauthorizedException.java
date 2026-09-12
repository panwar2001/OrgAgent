package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that a caller is not authenticated.
 *
 * <p>Reserved for the authentication work that follows; the API is currently open.
 */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException(String message) {
		super(requireStatus(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED), message);
	}

}
