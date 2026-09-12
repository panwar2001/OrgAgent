package com.panwar2001.orgagent.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.panwar2001.orgagent.core.exception.ApiError;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.json.JsonMapper;

class ApiErrorWriterTest {

	private final ApiErrorWriter writer = new ApiErrorWriter(JsonMapper.builder().build());

	@Test
	void writesTheStandardErrorPayloadAsJson() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(response, ApiError.of(ErrorCode.PROJECT_NOT_FOUND, "Project not found: 7", "/api/v1/projects/7"));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(response.getContentType()).contains("application/json");
		assertThat(response.getContentAsString()).contains("\"status\":404")
			.contains("\"code\":\"PROJECT_NOT_FOUND\"")
			.contains("\"message\":\"Project not found: 7\"")
			.contains("\"path\":\"/api/v1/projects/7\"");
	}

	@Test
	void authenticationEntryPointAnswersWith401Json() throws Exception {
		MockHttpServletRequest request = get("/api/v1/organizations");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ApiAuthenticationEntryPoint(writer).commence(request, response,
				new BadCredentialsException("no token supplied"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"")
			.contains("\"path\":\"/api/v1/organizations\"");
	}

	@Test
	void accessDeniedHandlerAnswersWith403Json() throws Exception {
		MockHttpServletRequest request = get("/api/v1/organizations/other");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ApiAccessDeniedHandler(writer).handle(request, response, new AccessDeniedException("not your project"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("\"code\":\"ACCESS_DENIED\"")
			.contains("\"path\":\"/api/v1/organizations/other\"");
	}

	@Test
	void neverLetsAWriteFailureEscapeTheFilterChain() {
		assertThatCode(() -> writer.write(new MockHttpServletResponse(),
				ApiError.of(ErrorCode.INTERNAL_ERROR, "boom", "/x"))).doesNotThrowAnyException();
	}

	private MockHttpServletRequest get(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setRequestURI(uri);
		return request;
	}

}
