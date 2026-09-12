package com.panwar2001.orgagent.core.exception;

import java.util.List;
import java.util.Optional;

import com.panwar2001.orgagent.core.exception.ApiError.FieldViolation;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates every exception that can escape a controller into the single {@link ApiError} shape.
 *
 * <p>Client mistakes are logged at debug level (they are not our incidents), server-side failures at
 * error level with the stack trace. Internal details are never put into the response body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalRestExceptionHandler {

	private static final String GLOBAL_FIELD = "_request";

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
		if (ex.status().is5xxServerError()) {
			log.error("Request failed [{}] {} {}: {}", ex.code(), request.getMethod(), request.getRequestURI(),
					ex.getMessage(), ex);
		}
		else {
			log.debug("Request rejected [{}] {} {}: {}", ex.code(), request.getMethod(), request.getRequestURI(),
					ex.getMessage());
		}
		return respond(ApiError.of(ex.code(), ex.getMessage(), path(request)));
	}

	// ---------- 400: validation ----------

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream().map(this::toViolation).toList();
		return badRequest(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, violations);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiError> handleBindException(BindException ex, HttpServletRequest request) {
		List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream().map(this::toViolation).toList();
		return badRequest(ErrorCode.VALIDATION_FAILED, "Request binding failed", request, violations);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleHandlerMethodValidation(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		List<FieldViolation> violations = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> FieldViolation.of(nameOf(result.getMethodParameter()), messageOf(error),
						result.getArgument())))
			.toList();
		return badRequest(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, violations);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		List<FieldViolation> violations = ex.getConstraintViolations()
			.stream()
			.map(violation -> FieldViolation.of(violation.getPropertyPath().toString(), violation.getMessage(),
					violation.getInvalidValue()))
			.toList();
		return badRequest(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, violations);
	}

	// ---------- 400 / 415: malformed requests ----------

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		String detail = Optional.ofNullable(ex.getMostSpecificCause()).map(Throwable::getMessage).orElse(ex.getMessage());
		return respond(ApiError.of(ErrorCode.MALFORMED_REQUEST, "Request body could not be parsed: " + detail,
				path(request)));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		String message = "Required parameter '%s' (%s) is missing".formatted(ex.getParameterName(),
				ex.getParameterType());
		return respond(ApiError.of(ErrorCode.MISSING_PARAMETER, message, path(request)));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		String required = Optional.ofNullable(ex.getRequiredType()).map(Class::getSimpleName).orElse("the expected type");
		String message = "Parameter '%s' must be of type %s but was '%s'".formatted(ex.getName(), required,
				ex.getValue());
		return respond(ApiError.of(ErrorCode.INVALID_PARAMETER, message, path(request)));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		String message = "Method %s is not supported by %s".formatted(ex.getMethod(), path(request));
		return respond(ApiError.of(ErrorCode.METHOD_NOT_ALLOWED, message, path(request)));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request) {
		String message = "Media type %s is not supported".formatted(ex.getContentType());
		return respond(ApiError.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message, path(request)));
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<ApiError> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
			HttpServletRequest request) {
		return respond(ApiError.of(ErrorCode.NOT_ACCEPTABLE, ex.getMessage(), path(request)));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
		String message = ex.getMaxUploadSize() > 0
				? "Upload exceeds the maximum allowed size of %d bytes".formatted(ex.getMaxUploadSize())
				: "Upload exceeds the maximum allowed size";
		return respond(ApiError.of(ErrorCode.PAYLOAD_TOO_LARGE, message, path(request)));
	}

	// ---------- 401 / 403 ----------

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return respond(ApiError.of(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), path(request)));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.debug("Access denied on {} {}", request.getMethod(), request.getRequestURI());
		return respond(ApiError.of(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), path(request)));
	}

	// ---------- 404 ----------

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
		String message = "No endpoint %s %s".formatted(request.getMethod(), path(request));
		return respond(ApiError.of(ErrorCode.RESOURCE_NOT_FOUND, message, path(request)));
	}

	// ---------- 409 ----------

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		return respond(ApiError.of(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage(), path(request)));
	}

	// ---------- anything Spring raised with an explicit status ----------

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
		int status = ex.getStatusCode().value();
		String message = Optional.ofNullable(ex.getReason()).orElse(HttpStatus.valueOf(status).getReasonPhrase());
		return respond(ApiError.of(status, "HTTP_" + status, message, path(request)));
	}

	// ---------- 500 ----------

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(ApiError.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), path(request)));
	}

	// ---------- helpers ----------

	private ResponseEntity<ApiError> badRequest(ErrorCode code, String message, HttpServletRequest request,
			List<FieldViolation> violations) {
		log.debug("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), violations);
		return respond(ApiError.of(code, message, path(request), violations));
	}

	private ResponseEntity<ApiError> respond(ApiError error) {
		return ResponseEntity.status(error.status()).body(error);
	}

	private FieldViolation toViolation(FieldError error) {
		return FieldViolation.of(error.getField(),
				Optional.ofNullable(error.getDefaultMessage()).orElse("invalid value"), error.getRejectedValue());
	}

	private String nameOf(org.springframework.core.MethodParameter parameter) {
		return Optional.ofNullable(parameter.getParameterName()).orElse(GLOBAL_FIELD);
	}

	private String messageOf(MessageSourceResolvable resolvable) {
		return Optional.ofNullable(resolvable.getDefaultMessage()).orElse("invalid value");
	}

	private String path(HttpServletRequest request) {
		return request.getRequestURI();
	}

}
