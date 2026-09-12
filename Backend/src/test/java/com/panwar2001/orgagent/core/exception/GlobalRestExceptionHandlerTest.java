package com.panwar2001.orgagent.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

class GlobalRestExceptionHandlerTest {

	private final GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler();

	@Test
	void mapsADomainExceptionToItsStatusAndCode() {
		ApiError error = body(handler.handleApiException(
				ResourceNotFoundException.of(ErrorCode.ORGANIZATION_NOT_FOUND, "acme"), get("/api/v1/organizations/acme")));

		assertThat(error.status()).isEqualTo(404);
		assertThat(error.code()).isEqualTo("ORGANIZATION_NOT_FOUND");
		assertThat(error.message()).isEqualTo("Organization not found: acme");
		assertThat(error.path()).isEqualTo("/api/v1/organizations/acme");
		assertThat(error.timestamp()).isNotNull();
	}

	@Test
	void mapsConflictsTo409() {
		ApiError error = body(handler.handleApiException(new ConflictException(ErrorCode.PROJECT_ALREADY_EXISTS, "taken"),
				post("/api/v1/organizations/acme/projects")));

		assertThat(error.status()).isEqualTo(409);
		assertThat(error.code()).isEqualTo("PROJECT_ALREADY_EXISTS");
	}

	@Test
	void reportsFieldLevelViolationsForAnInvalidBody() throws Exception {
		MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter("name"),
				bindingWithError("name", "must not be blank", ""));

		ApiError error = body(handler.handleMethodArgumentNotValid(exception, post("/api/v1/organizations")));

		assertThat(error.status()).isEqualTo(400);
		assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(error.violations()).singleElement().satisfies(violation -> {
			assertThat(violation.field()).isEqualTo("name");
			assertThat(violation.message()).isEqualTo("must not be blank");
			assertThat(violation.rejectedValue()).isEqualTo("");
		});
	}

	@Test
	void reportsConstraintViolationsRaisedOnMethodParameters() {
		ConstraintViolation<?> violation = mock(ConstraintViolation.class);
		Path path = mock(Path.class);
		when(path.toString()).thenReturn("list.size");
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn("must be between 1 and 50");
		when(violation.getInvalidValue()).thenReturn(0);

		ApiError error = body(handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)),
				get("/api/v1/organizations")));

		assertThat(error.status()).isEqualTo(400);
		assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(error.violations()).singleElement()
			.satisfies(v -> assertThat(v.field()).isEqualTo("list.size"));
	}

	@Test
	void reportsMissingRequestParameters() {
		ApiError error = body(handler.handleMissingParameter(
				new MissingServletRequestParameterException("projectId", "UUID"), get("/api/v1/chat")));

		assertThat(error.status()).isEqualTo(400);
		assertThat(error.code()).isEqualTo("MISSING_PARAMETER");
		assertThat(error.message()).contains("projectId").contains("UUID");
	}

	@Test
	void reportsTypeMismatches() throws Exception {
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("abc", Integer.class,
				"topK", parameter("topK"), new NumberFormatException("abc"));

		ApiError error = body(handler.handleTypeMismatch(exception, get("/api/v1/chat")));

		assertThat(error.status()).isEqualTo(400);
		assertThat(error.code()).isEqualTo("INVALID_PARAMETER");
		assertThat(error.message()).contains("topK").contains("Integer");
	}

	@Test
	void reportsUnsupportedHttpMethods() {
		ApiError error = body(handler.handleMethodNotSupported(
				new HttpRequestMethodNotSupportedException("PATCH"), patch("/api/v1/organizations")));

		assertThat(error.status()).isEqualTo(405);
		assertThat(error.code()).isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	void reportsUploadsThatExceedTheConfiguredLimit() {
		ApiError error = body(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1048576L),
				post("/api/v1/projects/1/documents")));

		assertThat(error.status()).isEqualTo(413);
		assertThat(error.code()).isEqualTo("PAYLOAD_TOO_LARGE");
		assertThat(error.message()).contains("1048576");
	}

	@Test
	void reportsDeniedAccessAsForbidden() {
		ApiError error = body(handler.handleAccessDenied(new AccessDeniedException("nope"),
				get("/api/v1/organizations/other")));

		assertThat(error.status()).isEqualTo(403);
		assertThat(error.code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void reportsUnknownRoutesAsNotFound() {
		ApiError error = body(handler.handleNoHandler(
				new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "/api/v1/nope"),
				get("/api/v1/nope")));

		assertThat(error.status()).isEqualTo(404);
		assertThat(error.code()).isEqualTo("RESOURCE_NOT_FOUND");
	}

	@Test
	void keepsTheStatusOfAProgrammaticResponseStatusException() {
		ApiError error = body(handler.handleResponseStatus(
				new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down"), get("/api/v1/chat")));

		assertThat(error.status()).isEqualTo(429);
		assertThat(error.code()).isEqualTo("HTTP_429");
		assertThat(error.message()).isEqualTo("slow down");
	}

	@Test
	void hidesInternalDetailsOfUnexpectedFailures() {
		ApiError error = body(handler.handleUnexpected(new IllegalStateException("connection string leaked"),
				get("/api/v1/chat")));

		assertThat(error.status()).isEqualTo(500);
		assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
		assertThat(error.message()).doesNotContain("connection string");
	}

	// ---------- helpers ----------

	private ApiError body(ResponseEntity<ApiError> response) {
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	private MethodParameter parameter(String methodName) throws Exception {
		Method method = SampleController.class.getDeclaredMethod(methodName, String.class);
		return new MethodParameter(method, 0);
	}

	private BeanPropertyBindingResult bindingWithError(String field, String message, Object rejectedValue) {
		BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
		binding.addError(new FieldError("request", field, rejectedValue, false, null, null, message));
		return binding;
	}

	private MockHttpServletRequest get(String uri) {
		return request("GET", uri);
	}

	private MockHttpServletRequest post(String uri) {
		return request("POST", uri);
	}

	private MockHttpServletRequest patch(String uri) {
		return request("PATCH", uri);
	}

	private MockHttpServletRequest request(String method, String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setRequestURI(uri);
		return request;
	}

	@SuppressWarnings("unused")
	private static final class SampleController {

		void name(String name) {
		}

		void topK(String topK) {
		}

	}

}
