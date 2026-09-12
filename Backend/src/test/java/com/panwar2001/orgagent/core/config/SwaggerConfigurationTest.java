package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class SwaggerConfigurationTest {

	private final OpenAPI openApi = new SwaggerConfiguration().orgAgentOpenApi();

	@Test
	void describesTheService() {
		assertThat(openApi.getInfo().getTitle()).isEqualTo("OrgAgent API");
		assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
		assertThat(openApi.getInfo().getDescription()).contains("RAG");
	}

	@Test
	void groupsEndpointsByFeatureTag() {
		assertThat(openApi.getTags()).extracting("name")
			.containsExactly("Organizations", "Projects", "Documents", "Chat");
	}

	@Test
	void declaresTheBearerSchemeWithoutRequiringItYet() {
		SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(SwaggerConfiguration.BEARER_SCHEME);

		assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
		assertThat(scheme.getScheme()).isEqualTo("bearer");
		assertThat(openApi.getSecurity()).isNull();
	}

}
