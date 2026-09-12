package com.panwar2001.orgagent.core.config;

import java.time.Duration;
import java.util.List;

import org.springframework.util.unit.DataSize;

/** Immutable properties used by unit tests, so they never depend on the YAML or a Spring context. */
public final class TestProperties {

	private TestProperties() {
	}

	public static OrgAgentProperties defaults() {
		return new OrgAgentProperties(defaultRag(), defaultCache(), defaultIngestion(), defaultCors());
	}

	public static OrgAgentProperties.Rag defaultRag() {
		return new OrgAgentProperties.Rag(20, Duration.ofHours(24), 6, 0.6);
	}

	public static OrgAgentProperties.Cache defaultCache() {
		return new OrgAgentProperties.Cache(
				new OrgAgentProperties.Cache.Semantic(true, 0.95, Duration.ofDays(7), 5000));
	}

	public static OrgAgentProperties.Ingestion defaultIngestion() {
		return new OrgAgentProperties.Ingestion(DataSize.ofMegabytes(25), 800, 120);
	}

	public static OrgAgentProperties.Cors defaultCors() {
		return new OrgAgentProperties.Cors(List.of("http://localhost:3000", "http://localhost:5173"),
				List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
				List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"), List.of("Location"), true,
				Duration.ofHours(1));
	}

}
