package com.panwar2001.orgagent.features.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.TestProperties;
import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.redis.ChatRole;
import com.panwar2001.orgagent.core.redis.ChatTurn;
import com.panwar2001.orgagent.core.redis.ChatWindowStore;
import com.panwar2001.orgagent.core.redis.RedisKeys;
import com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache;
import com.panwar2001.orgagent.features.chat.dto.AskQuestionRequest;
import com.panwar2001.orgagent.features.chat.dto.ChatAnswerResponse;
import com.panwar2001.orgagent.features.chat.llm.AnswerGenerator;
import com.panwar2001.orgagent.features.chat.llm.GeneratedAnswer;
import com.panwar2001.orgagent.features.chat.rag.RagPromptBuilder;
import com.panwar2001.orgagent.features.chat.rag.RagRetriever;
import com.panwar2001.orgagent.features.chat.rag.RetrievedChunk;
import com.panwar2001.orgagent.features.project.Project;
import com.panwar2001.orgagent.features.project.ProjectService;
import com.panwar2001.orgagent.features.project.ProjectStatus;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.Prompt;

class ChatServiceTest {

	private final ProjectService projectService = mock(ProjectService.class);

	private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);

	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);

	private final ChatWindowStore windowStore = mock(ChatWindowStore.class);

	private final RagRetriever retriever = mock(RagRetriever.class);

	private final RagPromptBuilder promptBuilder = new RagPromptBuilder();

	private final AnswerGenerator answerGenerator = mock(AnswerGenerator.class);

	private final SemanticAnswerCache answerCache = mock(SemanticAnswerCache.class);

	private final ChatPersistenceService persistence = mock(ChatPersistenceService.class);

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	private final ChatService service = new ChatService(this.projectService, this.conversations, this.messages,
			this.windowStore, this.retriever, this.promptBuilder, this.answerGenerator, this.answerCache, this.persistence,
			TestProperties.defaults());

	@Test
	void readsTheLiveWindowFromRedisAndAnswersFromTheModel() {
		ChatConversation conversation = givenExistingConversation();
		List<ChatTurn> window = List.of(new ChatTurn(ChatRole.USER, "earlier question", Instant.now()));
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(window);
		when(this.answerCache.find(this.organizationId, this.projectId, "how long do refunds take?"))
			.thenReturn(Optional.empty());
		UUID documentId = UUID.randomUUID();
		when(this.retriever.retrieve(this.projectId, "how long do refunds take?")).thenReturn(
				List.of(new RetrievedChunk(documentId.toString(), "Refund policy", "Five working days.", 0.9)));
		when(this.answerGenerator.generate(any(Prompt.class))).thenReturn(new GeneratedAnswer("Five days.", "model-x"));

		ChatAnswerResponse response = this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("how long do refunds take?", conversation.getId()));

		assertThat(response.answer()).isEqualTo("Five days.");
		assertThat(response.fromCache()).isFalse();
		assertThat(response.model()).isEqualTo("model-x");
		assertThat(response.sources()).singleElement().satisfies(source -> {
			assertThat(source.title()).isEqualTo("Refund policy");
			assertThat(source.documentId()).isEqualTo(documentId);
			assertThat(source.score()).isEqualTo(0.9);
		});

		verify(this.windowStore).recent(RedisKeys.chatWindow(this.organizationId.toString(), this.projectId.toString(),
				conversation.getId().toString()), 20);
	}

	@Test
	void putsTheRedisWindowIntoThePromptSoFollowUpsKeepTheirContext() {
		ChatConversation conversation = givenExistingConversation();
		List<ChatTurn> window = List.of(new ChatTurn(ChatRole.USER, "what is the refund window?", Instant.now()),
				new ChatTurn(ChatRole.ASSISTANT, "Five working days.", Instant.now()));
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(window);
		when(this.answerCache.find(any(), any(), any())).thenReturn(Optional.empty());
		when(this.retriever.retrieve(any(), any())).thenReturn(List.of());
		when(this.answerGenerator.generate(any(Prompt.class))).thenReturn(new GeneratedAnswer("answer", "m"));

		this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("and for gift cards?", conversation.getId()));

		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(this.answerGenerator).generate(prompt.capture());
		assertThat(prompt.getValue().getInstructions()).extracting(message -> message.getText())
			.contains("what is the refund window?", "Five working days.", "and for gift cards?");
	}

	@Test
	void answersFromTheSemanticCacheWithoutRetrievingOrCallingTheModel() {
		ChatConversation conversation = givenExistingConversation();
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(List.of());
		when(this.answerCache.find(this.organizationId, this.projectId, "how long do refunds take?"))
			.thenReturn(Optional.of("Five working days."));

		ChatAnswerResponse response = this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("how long do refunds take?", conversation.getId()));

		assertThat(response.answer()).isEqualTo("Five working days.");
		assertThat(response.fromCache()).isTrue();
		assertThat(response.sources()).isEmpty();
		assertThat(response.model()).isNull();

		verify(this.retriever, never()).retrieve(any(), any());
		verify(this.answerGenerator, never()).generate(any());
	}

	@Test
	void stillRecordsATurnThatWasAnsweredFromTheCache() {
		ChatConversation conversation = givenExistingConversation();
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(List.of());
		when(this.answerCache.find(any(), any(), any())).thenReturn(Optional.of("cached"));

		this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("question", conversation.getId()));

		verify(this.persistence).appendToWindow(any(), any(ChatTurn.class), any(ChatTurn.class));
		verify(this.persistence).persistTurn(any(ChatMessage.class), any(ChatMessage.class));
	}

	@Test
	void writesTheTurnAsynchronouslyAfterAnswering() {
		ChatConversation conversation = givenExistingConversation();
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(List.of());
		when(this.answerCache.find(any(), any(), any())).thenReturn(Optional.empty());
		when(this.retriever.retrieve(any(), any())).thenReturn(List.of());
		when(this.answerGenerator.generate(any(Prompt.class))).thenReturn(new GeneratedAnswer("the answer", "m"));

		this.service.ask(this.organizationId, this.projectId, new AskQuestionRequest("question", conversation.getId()));

		verify(this.persistence).appendToWindow(any(), any(ChatTurn.class), any(ChatTurn.class));

		ArgumentCaptor<ChatMessage> logged = ArgumentCaptor.forClass(ChatMessage.class);
		verify(this.persistence, org.mockito.Mockito.times(1)).persistTurn(logged.capture(), logged.capture());
		assertThat(logged.getAllValues()).extracting(ChatMessage::getRole)
			.containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
		assertThat(logged.getAllValues().get(0).getCreatedAt())
			.isBefore(logged.getAllValues().get(1).getCreatedAt());

		verify(this.persistence).cacheAnswer(this.organizationId, this.projectId, "question", "the answer");
	}

	@Test
	void startsANewConversationWhenNoneIsGiven() {
		givenActiveProject();
		when(this.conversations.save(any(ChatConversation.class))).thenAnswer(call -> call.getArgument(0));
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(List.of());
		when(this.answerCache.find(any(), any(), any())).thenReturn(Optional.empty());
		when(this.retriever.retrieve(any(), any())).thenReturn(List.of());
		when(this.answerGenerator.generate(any(Prompt.class))).thenReturn(new GeneratedAnswer("answer", "m"));

		ChatAnswerResponse response = this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("What is the refund window?", null));

		assertThat(response.conversationId()).isNotNull();
		ArgumentCaptor<ChatConversation> saved = ArgumentCaptor.forClass(ChatConversation.class);
		verify(this.conversations).save(saved.capture());
		assertThat(saved.getValue().getTitle()).isEqualTo("What is the refund window?");
	}

	@Test
	void refusesToContinueAConversationOfAnotherProject() {
		givenActiveProject();
		UUID conversationId = UUID.randomUUID();
		when(this.conversations.findByIdAndOrganizationIdAndProjectId(conversationId, this.organizationId, this.projectId))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("question", conversationId)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void refusesToAnswerForAnArchivedProject() {
		when(this.projectService.require(this.organizationId, this.projectId))
			.thenReturn(Project.of(this.projectId, this.organizationId, "HR", "hr", "d", ProjectStatus.ARCHIVED));

		assertThatThrownBy(() -> this.service.ask(this.organizationId, this.projectId,
				new AskQuestionRequest("question", null)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("archived");

		verify(this.answerGenerator, never()).generate(any());
	}

	@Test
	void scopesRetrievalToTheProject() {
		ChatConversation conversation = givenExistingConversation();
		when(this.windowStore.recent(any(), any(Integer.class))).thenReturn(List.of());
		when(this.answerCache.find(any(), any(), any())).thenReturn(Optional.empty());
		when(this.retriever.retrieve(any(), any())).thenReturn(List.of());
		when(this.answerGenerator.generate(any(Prompt.class))).thenReturn(new GeneratedAnswer("answer", "m"));

		this.service.ask(this.organizationId, this.projectId, new AskQuestionRequest("question", conversation.getId()));

		verify(this.retriever).retrieve(this.projectId, "question");
	}

	@Test
	void returnsTheLiveWindowWithTheStoredTurns() {
		ChatConversation conversation = givenExistingConversation();
		when(this.windowStore.recent(any(), any(Integer.class)))
			.thenReturn(List.of(new ChatTurn(ChatRole.USER, "hi", Instant.parse("2026-01-01T00:00:00Z"))));

		var window = this.service.window(this.organizationId, this.projectId, conversation.getId());

		assertThat(window.turns()).singleElement().satisfies(turn -> {
			assertThat(turn.role()).isEqualTo("USER");
			assertThat(turn.content()).isEqualTo("hi");
		});
	}

	@Test
	void deletesTheWindowTogetherWithTheConversation() {
		ChatConversation conversation = givenExistingConversation();

		this.service.delete(this.organizationId, this.projectId, conversation.getId());

		verify(this.windowStore).clear(RedisKeys.chatWindow(this.organizationId.toString(), this.projectId.toString(),
				conversation.getId().toString()));
		verify(this.conversations).delete(conversation);
	}

	@Test
	void readsThePermanentLogFromPostgres() {
		ChatConversation conversation = givenExistingConversation();
		when(this.messages.findByConversationIdOrderByCreatedAtAsc(eq(conversation.getId()),
				any(org.springframework.data.domain.Pageable.class)))
			.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
					ChatMessage.of(UUID.randomUUID(), conversation.getId(), ChatRole.USER, "hi", Instant.now()))));

		var history = this.service.history(this.organizationId, this.projectId, conversation.getId(),
				org.springframework.data.domain.PageRequest.of(0, 50));

		assertThat(history.content()).singleElement()
			.satisfies(message -> assertThat(message.content()).isEqualTo("hi"));
	}

	private void givenActiveProject() {
		when(this.projectService.require(this.organizationId, this.projectId))
			.thenReturn(Project.of(this.projectId, this.organizationId, "HR", "hr", "d", ProjectStatus.ACTIVE));
	}

	private ChatConversation givenExistingConversation() {
		givenActiveProject();
		ChatConversation conversation = ChatConversation.of(UUID.randomUUID(), this.organizationId, this.projectId,
				"Refund window");
		when(this.conversations.findByIdAndOrganizationIdAndProjectId(conversation.getId(), this.organizationId,
				this.projectId)).thenReturn(Optional.of(conversation));
		return conversation;
	}

}
