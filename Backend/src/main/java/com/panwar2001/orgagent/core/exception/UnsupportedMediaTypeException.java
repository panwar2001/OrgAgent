package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals that the uploaded file is of a type this service cannot read. */
public class UnsupportedMediaTypeException extends ApiException {

	public UnsupportedMediaTypeException(String message) {
		super(requireStatus(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE), message);
	}

}
