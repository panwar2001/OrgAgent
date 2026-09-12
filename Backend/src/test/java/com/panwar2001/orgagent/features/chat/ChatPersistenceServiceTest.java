package com.panwar2001.orgagent.features.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.panwar2001.orgagent.core.redis.ChatRole;
import com.panwar2001.orgagent.core.redis.ChatTurn;
import com.panwar2001.orgagent.core.redis.ChatWindowStore;
import com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

class ChatPersistenceServiceTest {

	private final ChatWindowStore windowStore = mock(ChatWindowStore.class);

	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);

	private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);

	private final SemanticAnswerCache answerCache = mock(SemanticAnswerCache.class);

	private final ChatPersistenceService service = new ChatPersistenceService(this.windowStore, this.messages,
			this.conversations, this.answerCache);

	private final UUID conversationId = UUID.randomUUID();

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	@Test
	void appendsTheQuestionThenTheAnswerSoTheWindowReadsInOrder() {
		ChatTurn question = ChatTurn.user("what is the refund window?");
		ChatTurn answer = ChatTurn.assistant("Five working days.");

		this.service.appendToWindow("key", question, answer);

		InOrder inOrder = Mockito.inOrder(this.windowStore);
		inOrder.verify(this.windowStore).append("key", question);
		inOrder.verify(this.windowStore).append("key", answer);
	}

	@Test
	void appendsBothTurnsToThePermanentLog() {
		ChatMessage question = ChatMessage.question(this.conversationId, this.organizationId, this.projectId, "q",
				Instant.now());
		ChatMessage answer = ChatMessage.answer(this.conversationId, this.organizationId, this.projectId, "a", false,
				"m", 42L, Instant.now());

		this.service.persistTurn(question, answer);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.List<ChatMessage>> captor = ArgumentCaptor.forClass(java.util.List.class);
		verify(this.messages).saveAll(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue()).containsExactly(question, answer);
		verify(this.conversations).touch(eq(this.conversationId), any(Instant.class));
	}

	@Test
	void remembersTheAnswerInTheSemanticCache() {
		this.service.cacheAnswer(this.organizationId, this.projectId, "q", "a");

		verify(this.answerCache).store(this.organizationId, this.projectId, "q", "a");
	}

	@Test
	void neverLetsAFailedWindowWriteEscape() {
		doThrow(new IllegalStateException("redis down")).when(this.windowStore).append(any(), any(ChatTurn.class));

		assertThatCode(() -> this.service.appendToWindow("key", ChatTurn.user("q"), ChatTurn.assistant("a")))
			.doesNotThrowAnyException();
	}

	@Test
	void neverLetsAFailedLogWriteEscape() {
		when(this.messages.saveAll(anyList())).thenThrow(new IllegalStateException("database down"));

		assertThatCode(() -> this.service.persistTurn(
				ChatMessage.question(this.conversationId, this.organizationId, this.projectId, "q", Instant.now()),
				ChatMessage.answer(this.conversationId, this.organizationId, this.projectId, "a", false, "m", 1L,
						Instant.now()))).doesNotThrowAnyException();
	}

	@Test
	void neverLetsAFailedCacheWriteEscape() {
		doThrow(new IllegalStateException("vector store down")).when(this.answerCache)
			.store(any(), any(), any(), any());

		assertThatCode(() -> this.service.cacheAnswer(this.organizationId, this.projectId, "q", "a"))
			.doesNotThrowAnyException();
	}

	@Test
	void logsTheAnswerRoleAndCacheFlagOfTheLoggedTurns() {
		ChatMessage answer = ChatMessage.answer(this.conversationId, this.organizationId, this.projectId, "from cache",
				true, null, null, Instant.parse("2026-01-01T00:00:00Z"));

		this.service.persistTurn(
				ChatMessage.question(this.conversationId, this.organizationId, this.projectId, "q",
						Instant.parse("2026-01-01T00:00:00Z")),
				answer);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.List<ChatMessage>> captor = ArgumentCaptor.forClass(java.util.List.class);
		verify(this.messages).saveAll(captor.capture());
		ChatMessage loggedAnswer = captor.getValue().get(1);
		org.assertj.core.api.Assertions.assertThat(loggedAnswer.getRole()).isEqualTo(ChatRole.ASSISTANT);
		org.assertj.core.api.Assertions.assertThat(loggedAnswer.isServedFromCache()).isTrue();
		org.assertj.core.api.Assertions.assertThat(loggedAnswer.getModel()).isNull();
	}

}
