package com.panwar2001.orgagent.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to API clients.
 *
 * <p>Every failure surfaced by the API carries one of these codes so clients never have to
 * parse human-readable text. The HTTP status is part of the contract too.
 */
public enum ErrorCode {

	// ---- 400 ----
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is malformed or unreadable"),
	MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "A required request parameter is missing"),
	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "A request parameter has an invalid value"),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "The request is not valid"),

	// ---- 401 / 403 ----
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to perform this action"),

	// ---- 404 ----
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Organization not found"),
	PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Document not found"),
	CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),

	// ---- 405 / 406 / 409 / 413 / 415 ----
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint"),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "No representation acceptable to the client could be produced"),
	CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource"),
	ORGANIZATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "An organization with this slug already exists"),
	PROJECT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A project with this slug already exists in the organization"),
	DOCUMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A document with this name has already been ingested"),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The uploaded payload exceeds the allowed size"),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The media type of the request is not supported"),

	// ---- 500 / 502 / 503 ----
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
	INGESTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be ingested"),
	EMBEDDING_FAILED(HttpStatus.BAD_GATEWAY, "The text could not be embedded by the AI provider"),
	LLM_FAILED(HttpStatus.BAD_GATEWAY, "The language model could not produce a response"),
	AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is currently unavailable");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String defaultMessage() {
		return this.defaultMessage;
	}

}
