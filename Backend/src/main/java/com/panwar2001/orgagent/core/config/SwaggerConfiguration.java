package com.panwar2001.orgagent.core.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at {@code /v3/api-docs} with Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>The bearer scheme is declared but not required yet: endpoints are open today, and the security
 * chain will start enforcing it when authentication lands.
 */
@Configuration(proxyBeanMethods = false)
public class SwaggerConfiguration {

	public static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI orgAgentOpenApi() {
		return new OpenAPI()
			.info(new Info().title("OrgAgent API")
				.version("v1")
				.description("""
						Multi-tenant RAG chatbot over an organization's project documents.

						Each organization owns projects; each project owns ingested documents that are \
						embedded into pgvector and answered from by the chat endpoints.""")
				.contact(new Contact().name("OrgAgent"))
				.license(new License().name("Proprietary")))
			.servers(List.of(new Server().url("/").description("Current host")))
			.tags(List.of(new Tag().name("Organizations").description("Manage organization accounts"),
					new Tag().name("Projects").description("Manage projects inside an organization"),
					new Tag().name("Documents").description("Ingest and inspect project documents"),
					new Tag().name("Chat").description("Ask questions grounded in the project's documents")))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.description("Reserved for the authentication layer; endpoints are open today.")));
	}

}
