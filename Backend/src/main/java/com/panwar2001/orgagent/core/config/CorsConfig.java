package com.panwar2001.orgagent.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin policy for browser clients.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean rather than a {@code WebMvcConfigurer} so the
 * security filter chain can apply CORS before authentication, which is where pre-flight requests
 * must be answered from once authentication is switched on.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(OrgAgentProperties properties) {
		OrgAgentProperties.Cors cors = properties.cors();

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(cors.allowedOrigins());
		configuration.setAllowedMethods(cors.allowedMethods());
		configuration.setAllowedHeaders(cors.allowedHeaders());
		configuration.setExposedHeaders(cors.exposedHeaders());
		configuration.setAllowCredentials(cors.allowCredentials());
		configuration.setMaxAge(cors.maxAge());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
