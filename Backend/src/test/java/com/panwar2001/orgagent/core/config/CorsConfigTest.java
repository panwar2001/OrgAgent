package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

	private final CorsConfigurationSource source = new CorsConfig()
		.corsConfigurationSource(TestProperties.defaults());

	@Test
	void allowsConfiguredOriginsAndRejectsOthers() {
		CorsConfiguration configuration = configuration("/api/v1/organizations");

		assertThat(configuration.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
		assertThat(configuration.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
		assertThat(configuration.checkOrigin("https://evil.example")).isNull();
	}

	@Test
	void allowsTheMethodsTheApiUses() {
		CorsConfiguration configuration = configuration("/api/v1/projects");

		assertThat(configuration.checkHttpMethod(HttpMethod.GET)).isNotNull();
		assertThat(configuration.checkHttpMethod(HttpMethod.POST)).isNotNull();
		assertThat(configuration.checkHttpMethod(HttpMethod.PATCH)).isNotNull();
		assertThat(configuration.checkHttpMethod(HttpMethod.DELETE)).isNotNull();
	}

	@Test
	void allowsAuthorizationAndContentTypeHeadersFromTheBrowser() {
		CorsConfiguration configuration = configuration("/api/v1/chat");

		assertThat(configuration.checkHeaders(java.util.List.of("authorization", "content-type"))).isNotNull();
		assertThat(configuration.checkHeaders(java.util.List.of("x-not-allowed"))).isNull();
	}

	@Test
	void appliesToEveryPath() {
		assertThat(configuration("/anything/at/all")).isNotNull();
	}

	@Test
	void cachesPreflightDecisions() {
		assertThat(configuration("/api/v1/organizations").getMaxAge()).isEqualTo(3600L);
		assertThat(configuration("/api/v1/organizations").getAllowCredentials()).isTrue();
	}

	private CorsConfiguration configuration(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), path);
		request.setRequestURI(path);
		CorsConfiguration configuration = this.source.getCorsConfiguration(request);
		assertThat(configuration).isNotNull();
		return configuration;
	}

}
