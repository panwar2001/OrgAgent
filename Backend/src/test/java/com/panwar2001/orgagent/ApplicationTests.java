package com.panwar2001.orgagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Guards the entry point of the service: the class the container launches must stay a
 * Spring Boot application and must expose a runnable {@code main} method.
 *
 * <p>Infrastructure-backed context tests are intentionally not used here: Postgres
 * (pgvector) and Redis are external dependencies of this service.
 */
class ApplicationTests {

	@Test
	void applicationClassIsASpringBootApplication() {
		assertThat(Application.class.getAnnotation(SpringBootApplication.class)).isNotNull();
	}

	@Test
	void applicationExposesAMainMethod() throws NoSuchMethodException {
		assertThat(Application.class.getMethod("main", String[].class)).isNotNull();
	}

}
