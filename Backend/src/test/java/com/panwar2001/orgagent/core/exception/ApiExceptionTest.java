package com.panwar2001.orgagent.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

	@Test
	void exposesTheCodeAndDerivesTheHttpStatusFromIt() {
		ApiException exception = new ApiException(ErrorCode.PROJECT_NOT_FOUND);

		assertThat(exception.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
		assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(exception).hasMessage(ErrorCode.PROJECT_NOT_FOUND.defaultMessage());
	}

	@Test
	void notFoundFactoryMentionsTheIdentifier() {
		ResourceNotFoundException exception = ResourceNotFoundException.of(ErrorCode.PROJECT_NOT_FOUND, "prj_42");

		assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(exception).hasMessage("Project not found: prj_42");
	}

	@Test
	void refusesACodeThatDoesNotMatchTheExceptionKind() {
		assertThatThrownBy(() -> new ResourceNotFoundException(ErrorCode.CONFLICT, "nope"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("CONFLICT");
	}

	@Test
	void llmFailuresAreReportedAsBadGatewayAndKeepTheirCause() {
		IllegalStateException cause = new IllegalStateException("quota exceeded");

		LlmException exception = new LlmException("model call failed", cause);

		assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(exception.code()).isEqualTo(ErrorCode.LLM_FAILED);
		assertThat(exception.getCause()).isSameAs(cause);
	}

	@Test
	void ingestionFailuresAreServerErrors() {
		IngestionException exception = new IngestionException("pdf unreadable");

		assertThat(exception.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(exception.code()).isEqualTo(ErrorCode.INGESTION_FAILED);
	}

	@Test
	void everyErrorCodeCarriesAStatusAndAMessage() {
		assertThat(ErrorCode.values()).allSatisfy(code -> {
			assertThat(code.status()).isNotNull();
			assertThat(code.defaultMessage()).isNotBlank();
		});
	}

}
