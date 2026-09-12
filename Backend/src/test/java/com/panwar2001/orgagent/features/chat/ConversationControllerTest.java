package com.panwar2001.orgagent.features.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.GlobalRestExceptionHandler;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationSummaryResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ConversationControllerTest {

	private final ChatService service = mock(ChatService.class);

	private final UUID organizationId = UUID.randomUUID();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(this.service))
			.setControllerAdvice(new GlobalRestExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void listsEveryConversationOfTheOrganizationWithItsProject() throws Exception {
		when(this.service.organizationConversations(eq(this.organizationId), any(Pageable.class)))
			.thenReturn(PageResponse.from(new PageImpl<>(List.of(new ConversationSummaryResponse(UUID.randomUUID(),
					this.organizationId, UUID.randomUUID(), "HR Policies", "How long do refunds take?",
					Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z")))),
					value -> value));

		this.mockMvc.perform(get("/api/v1/organizations/{organizationId}/conversations", this.organizationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("How long do refunds take?"))
			.andExpect(jsonPath("$.content[0].projectName").value("HR Policies"));
	}

	@Test
	void asksForTheMostRecentlyUsedSessionsFirst() throws Exception {
		when(this.service.organizationConversations(eq(this.organizationId), any(Pageable.class)))
			.thenReturn(PageResponse.from(new PageImpl<ConversationSummaryResponse>(List.of()), value -> value));

		this.mockMvc.perform(get("/api/v1/organizations/{organizationId}/conversations", this.organizationId))
			.andExpect(status().isOk());

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).organizationConversations(eq(this.organizationId), pageable.capture());
		assertThatSort(pageable.getValue());
	}

	@Test
	void turnsAnUnknownOrganizationInto404() throws Exception {
		when(this.service.organizationConversations(eq(this.organizationId), any(Pageable.class)))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.ORGANIZATION_NOT_FOUND, this.organizationId));

		this.mockMvc.perform(get("/api/v1/organizations/{organizationId}/conversations", this.organizationId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
	}

	private void assertThatSort(Pageable pageable) {
		org.assertj.core.api.Assertions.assertThat(pageable.getSort().getOrderFor("updatedAt"))
			.isNotNull()
			.satisfies(order -> org.assertj.core.api.Assertions.assertThat(order.getDirection())
				.isEqualTo(Sort.Direction.DESC));
	}

}
