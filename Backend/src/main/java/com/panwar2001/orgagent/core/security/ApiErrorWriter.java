package com.panwar2001.orgagent.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.panwar2001.orgagent.core.exception.ApiError;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Serialises an {@link ApiError} onto a servlet response.
 *
 * <p>The security filter chain sits in front of Spring MVC, so its rejections never reach
 * {@code GlobalRestExceptionHandler}. This writer is what keeps a 401 or 403 from the filter chain
 * looking exactly like every other error the API returns.
 */
@Component
public class ApiErrorWriter {

	private final ObjectMapper json;

	public ApiErrorWriter(ObjectMapper json) {
		this.json = json;
	}

	public void write(HttpServletResponse response, ApiError error) throws IOException {
		response.setStatus(error.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(this.json.writeValueAsString(error));
	}

}
