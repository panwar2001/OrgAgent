package com.panwar2001.orgagent.features.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.GlobalRestExceptionHandler;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.redis.ChatTurn;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.chat.dto.AskQuestionRequest;
import com.panwar2001.orgagent.features.chat.dto.ChatAnswerResponse;
import com.panwar2001.orgagent.features.chat.dto.ChatMessageResponse;
import com.panwar2001.orgagent.features.chat.dto.ChatSource;
import com.panwar2001.orgagent.features.chat.dto.ConversationResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationWindowResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ChatControllerTest {

	private final ChatService service = mock(ChatService.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private final UUID conversationId = UUID.randomUUID();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(this.service))
			.setControllerAdvice(new GlobalRestExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void answersAQuestionWithItsSources() throws Exception {
		when(this.service.ask(eq(this.organizationId), eq(this.projectId), any(AskQuestionRequest.class)))
			.thenReturn(new ChatAnswerResponse(this.conversationId, "Five working days.", false,
					List.of(new ChatSource(UUID.randomUUID(), "Refund policy", 0.91)), "gemini-2.5-flash", 812L,
					Instant.parse("2026-01-01T00:00:00Z")));

		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects/{projectId}/chat", this.organizationId,
					this.projectId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"How long do refunds take?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answer").value("Five working days."))
			.andExpect(jsonPath("$.fromCache").value(false))
			.andExpect(jsonPath("$.sources[0].title").value("Refund policy"))
			.andExpect(jsonPath("$.conversationId").value(this.conversationId.toString()));
	}

	@Test
	void reportsACacheHitWithoutSources() throws Exception {
		when(this.service.ask(eq(this.organizationId), eq(this.projectId), any(AskQuestionRequest.class)))
			.thenReturn(new ChatAnswerResponse(this.conversationId, "cached answer", true, List.of(), null, null,
					Instant.parse("2026-01-01T00:00:00Z")));

		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects/{projectId}/chat", this.organizationId,
					this.projectId).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"same again\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fromCache").value(true))
			.andExpect(jsonPath("$.sources").isEmpty());
	}

	@Test
	void rejectsAnEmptyQuestion() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects/{projectId}/chat", this.organizationId,
					this.projectId).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.violations[0].field").value("question"));
	}

	@Test
	void returnsTheLiveWindowFromRedis() throws Exception {
		when(this.service.window(this.organizationId, this.projectId, this.conversationId))
			.thenReturn(ConversationWindowResponse.of(this.conversationId,
					List.of(ChatTurn.user("earlier question"))));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/chat/{conversationId}",
					this.organizationId, this.projectId, this.conversationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.turns[0].role").value("USER"))
			.andExpect(jsonPath("$.turns[0].content").value("earlier question"));
	}

	@Test
	void returnsThePermanentHistory() throws Exception {
		when(this.service.history(eq(this.organizationId), eq(this.projectId), eq(this.conversationId),
				any(Pageable.class)))
			.thenReturn(PageResponse.from(
					new PageImpl<>(List.of(new ChatMessageResponse(UUID.randomUUID(), this.conversationId, "USER",
							"hi", false, null, null, Instant.parse("2026-01-01T00:00:00Z")))),
					value -> value));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/chat/{conversationId}/history",
					this.organizationId, this.projectId, this.conversationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].role").value("USER"));
	}

	@Test
	void listsTheConversationsOfAProject() throws Exception {
		when(this.service.conversations(eq(this.organizationId), eq(this.projectId), any(Pageable.class)))
			.thenReturn(PageResponse.from(new PageImpl<>(List.of(new ConversationResponse(this.conversationId,
					this.organizationId, this.projectId, "Refund window", Instant.parse("2026-01-01T00:00:00Z"),
					Instant.parse("2026-01-01T00:00:00Z")))), value -> value));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/chat", this.organizationId,
					this.projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("Refund window"));
	}

	@Test
	void turnsAnUnknownConversationInto404() throws Exception {
		when(this.service.window(this.organizationId, this.projectId, this.conversationId))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.CONVERSATION_NOT_FOUND, this.conversationId));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}/chat/{conversationId}",
					this.organizationId, this.projectId, this.conversationId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
	}

	@Test
	void deletesAConversation() throws Exception {
		this.mockMvc
			.perform(delete("/api/v1/organizations/{organizationId}/projects/{projectId}/chat/{conversationId}",
					this.organizationId, this.projectId, this.conversationId))
			.andExpect(status().isNoContent());

		verify(this.service).delete(this.organizationId, this.projectId, this.conversationId);
	}

}
