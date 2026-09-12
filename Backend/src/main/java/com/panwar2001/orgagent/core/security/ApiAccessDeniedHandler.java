package com.panwar2001.orgagent.core.security;

import java.io.IOException;

import com.panwar2001.orgagent.core.exception.ApiError;
import com.panwar2001.orgagent.core.exception.ErrorCode;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Answers authenticated-but-not-allowed filter-chain requests with the API's JSON error shape.
 *
 * <p>Once organizations own their data, this is the response another tenant's project gets.
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final ApiErrorWriter errorWriter;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException cause)
			throws IOException {
		this.errorWriter.write(response,
				ApiError.of(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), request.getRequestURI()));
	}

}
