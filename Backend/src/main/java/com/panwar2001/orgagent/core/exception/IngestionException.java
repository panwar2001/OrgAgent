package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/** Signals that a document could not be read, chunked or embedded during ingestion. */
public class IngestionException extends ApiException {

	public IngestionException(String message) {
		super(requireStatus(ErrorCode.INGESTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR), message);
	}

	public IngestionException(String message, Throwable cause) {
		super(requireStatus(ErrorCode.INGESTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR), message, cause);
	}

	public IngestionException(ErrorCode code, String message, Throwable cause) {
		super(requireStatus(code, HttpStatus.INTERNAL_SERVER_ERROR), message, cause);
	}

}
