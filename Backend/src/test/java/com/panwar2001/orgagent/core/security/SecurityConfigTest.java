package com.panwar2001.orgagent.core.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panwar2001.orgagent.core.config.CorsConfig;
import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import jakarta.servlet.Filter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the real filter chain in a servlet context and drives requests through it.
 *
 * <p>No database or Redis is involved: this only proves the authorization rules and the JSON
 * rejections, which is exactly the part of security that is easy to get subtly wrong.
 */
class SecurityConfigTest {

	private AnnotationConfigWebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.context = new AnnotationConfigWebApplicationContext();
		this.context.setServletContext(new MockServletContext());
		this.context.register(Stubs.class, CorsConfig.class, SecurityConfig.class, ApiErrorWriter.class,
				ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class);
		this.context.refresh();

		Filter securityFilterChain = this.context.getBean("springSecurityFilterChain", Filter.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup().apply(springSecurity(securityFilterChain)).build();
	}

	@AfterEach
	void tearDown() {
		this.context.close();
	}

	/** Only the collaborators the security chain needs, so no database or Redis is involved. */
	@Configuration(proxyBeanMethods = false)
	static class Stubs {

		@Bean
		ObjectMapper objectMapper() {
			return JsonMapper.builder().build();
		}

		@Bean
		OrgAgentProperties orgAgentProperties() {
			return TestProperties.defaults();
		}

	}

	@Test
	void letsBusinessEndpointsThroughWhileAuthenticationIsOff() throws Exception {
		// Reaching MVC (404: no controllers are registered here) proves security let it past.
		this.mockMvc.perform(get("/api/v1/organizations")).andExpect(status().isNotFound());
		this.mockMvc.perform(post("/api/v1/organizations")).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/v1/projects/7/chat")).andExpect(status().isNotFound());
	}

	@Test
	void keepsTheApiDocumentationPublic() throws Exception {
		this.mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}

	@Test
	void answersUnauthenticatedCallersToUnlistedPathsWith401Json() throws Exception {
		// Anonymous callers are challenged rather than told the resource exists.
		this.mockMvc.perform(get("/actuator/env"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.path").value("/actuator/env"));
	}

	@Test
	void answersAuthenticatedCallersToUnlistedPathsWith403Json() throws Exception {
		this.mockMvc.perform(get("/internal/secrets").with(user("someone")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
			.andExpect(jsonPath("$.path").value("/internal/secrets"))
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

}
