package com.panwar2001.orgagent.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The API security filter chain.
 *
 * <p><strong>Current posture:</strong> the business endpoints are deliberately open — authentication
 * is a later step. What is already fixed is everything that is expensive to change afterwards:
 * stateless sessions (no server-side session to migrate), CSRF disabled for a token API, CORS
 * answered before authorization so pre-flight requests succeed, and deny-by-default for any path
 * that is not explicitly listed. Turning authentication on is then a matter of replacing the
 * {@code /api/**} rule, not of re-plumbing the chain.
 *
 * <p>Rejections are written with {@link ApiErrorWriter} so a 401/403 from the chain is the same JSON
 * shape as every other API error.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/** Endpoints that stay reachable without authentication even once it exists. */
	static final String[] PUBLIC_ENDPOINTS = { "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error" };

	/** Business endpoints, open until an authentication mechanism is chosen. */
	static final String API_ENDPOINTS = "/api/**";

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
			ApiAuthenticationEntryPoint authenticationEntryPoint, ApiAccessDeniedHandler accessDeniedHandler)
			throws Exception {
		http
			// Pre-flight and cross-origin calls must be resolved before any authorization rule runs.
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			// Token-based, stateless API: there is no browser session or cookie to forge.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.OPTIONS, "/**")
				.permitAll()
				.requestMatchers(PUBLIC_ENDPOINTS)
				.permitAll()
				// TODO: replace with .authenticated() once accounts and tokens exist.
				.requestMatchers(API_ENDPOINTS)
				.permitAll()
				.anyRequest()
				.denyAll())
			.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));

		return http.build();
	}

}
