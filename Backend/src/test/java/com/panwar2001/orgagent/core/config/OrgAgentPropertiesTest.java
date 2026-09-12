package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class OrgAgentPropertiesTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void acceptsTheValuesShippedInApplicationYaml() {
		assertThat(validator.validate(TestProperties.defaults())).isEmpty();
	}

	@Test
	void rejectsAWindowTooSmallToCarryConversationContext() {
		OrgAgentProperties properties = new OrgAgentProperties(new OrgAgentProperties.Rag(0, Duration.ofHours(1), 6, 0.6),
				TestProperties.defaultCache(), TestProperties.defaultIngestion(), TestProperties.defaultCors());

		assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
			.containsExactly("rag.chatWindowSize");
	}

	@Test
	void rejectsRetrievalThatWouldPullNothingBack() {
		OrgAgentProperties properties = new OrgAgentProperties(new OrgAgentProperties.Rag(20, Duration.ofHours(1), 0, 0.6),
				TestProperties.defaultCache(), TestProperties.defaultIngestion(), TestProperties.defaultCors());

		assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
			.containsExactly("rag.retrievalTopK");
	}

	@Test
	void rejectsCorsWithoutAnyAllowedOrigin() {
		OrgAgentProperties.Cors cors = new OrgAgentProperties.Cors(List.of(), List.of("GET"), List.of("Accept"),
				List.of(), true, Duration.ofHours(1));
		OrgAgentProperties properties = new OrgAgentProperties(TestProperties.defaultRag(), TestProperties.defaultCache(),
				TestProperties.defaultIngestion(), cors);

		Set<ConstraintViolation<OrgAgentProperties>> violations = validator.validate(properties);

		assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("cors.allowedOrigins");
	}

	@Test
	void rejectsChunksSmallerThanTheMinimumUsefulPassage() {
		OrgAgentProperties.Ingestion ingestion = new OrgAgentProperties.Ingestion(DataSize.ofMegabytes(25), 10, 0);
		OrgAgentProperties properties = new OrgAgentProperties(TestProperties.defaultRag(), TestProperties.defaultCache(),
				ingestion, TestProperties.defaultCors());

		assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
			.containsExactly("ingestion.chunkSize");
	}

}
