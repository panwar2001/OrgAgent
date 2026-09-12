package com.panwar2001.orgagent.core.security;

import java.io.IOException;

import com.panwar2001.orgagent.core.exception.ApiError;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers unauthenticated filter-chain requests with the API's JSON error shape instead of Spring
 * Security's default HTML or empty body.
 *
 * <p>Dormant while the API is open; it takes effect the moment authentication is required.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ApiErrorWriter errorWriter;

	public ApiAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException cause)
			throws IOException {
		this.errorWriter.write(response,
				ApiError.of(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), request.getRequestURI()));
	}

}
