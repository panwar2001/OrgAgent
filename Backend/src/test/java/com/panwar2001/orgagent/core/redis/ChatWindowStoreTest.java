package com.panwar2001.orgagent.core.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.config.TestProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ChatWindowStoreTest {

	private static final String KEY = "orgagent:org:1:project:2:chat:3:window";

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

	@SuppressWarnings("unchecked")
	private final ListOperations<String, String> list = mock(ListOperations.class);

	private final ObjectMapper json = JsonMapper.builder().build();

	private final OrgAgentProperties properties = TestProperties.defaults();

	private final ChatWindowStore store = new ChatWindowStore(this.redis, this.json, this.properties);

	@BeforeEach
	void setUp() {
		when(this.redis.opsForList()).thenReturn(this.list);
	}

	@Test
	void writesATurnJsonEncodedAndTrimsToTheConfiguredWindow() {
		store.append(KEY, ChatTurn.user("what is our refund policy?"));

		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(this.list).leftPush(eq(KEY), payload.capture());

		assertThat(payload.getValue()).contains("\"role\":\"USER\"").contains("what is our refund policy?");

		// Newest first in Redis, so the window keeps indexes 0..19 (configured size is 20).
		verify(this.list).trim(KEY, 0, 19);
		verify(this.redis).expire(KEY, Duration.ofHours(24));
	}

	@Test
	void returnsTurnsOldestFirstSoAPromptReadsNaturally() {
		ChatTurn question = ChatTurn.user("first question");
		ChatTurn answer = ChatTurn.assistant("first answer");
		when(this.list.range(KEY, 0, 19)).thenReturn(List.of(encode(answer), encode(question)));

		assertThat(store.recent(KEY)).containsExactly(question, answer);
	}

	@Test
	void readsOnlyTheRequestedNumberOfTurns() {
		when(this.list.range(KEY, 0, 4)).thenReturn(List.of());

		store.recent(KEY, 5);

		verify(this.list).range(KEY, 0, 4);
	}

	@Test
	void neverReadsMoreThanTheWindowHolds() {
		when(this.list.range(KEY, 0, 19)).thenReturn(List.of());

		store.recent(KEY, 500);

		verify(this.list).range(KEY, 0, 19);
	}

	@Test
	void readsAtLeastOneTurnEvenIfAskedForZero() {
		when(this.list.range(KEY, 0, 0)).thenReturn(List.of());

		store.recent(KEY, 0);

		verify(this.list).range(KEY, 0, 0);
	}

	@Test
	void returnsAnEmptyWindowWhenNothingIsStored() {
		when(this.list.range(KEY, 0, 19)).thenReturn(null);

		assertThat(store.recent(KEY)).isEmpty();
	}

	@Test
	void skipsUnreadableTurnsInsteadOfFailingTheConversation() {
		ChatTurn answer = ChatTurn.assistant("still readable");
		when(this.list.range(KEY, 0, 19)).thenReturn(List.of(encode(answer), "{not json"));

		assertThat(store.recent(KEY)).containsExactly(answer);
	}

	@Test
	void reportsTheWindowSizeAndClearsIt() {
		when(this.list.size(KEY)).thenReturn(7L);

		assertThat(store.size(KEY)).isEqualTo(7L);

		store.clear(KEY);

		verify(this.redis).delete(KEY);
	}

	@Test
	void reportsZeroWhenTheWindowKeyIsGone() {
		when(this.list.size(KEY)).thenReturn(null);

		assertThat(store.size(KEY)).isZero();
	}

	@Test
	void keepsTheTimestampOfEachTurn() {
		ChatTurn turn = new ChatTurn(ChatRole.SYSTEM, "context injected", Instant.parse("2026-01-02T03:04:05Z"));

		store.append(KEY, turn);

		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(this.list).leftPush(eq(KEY), payload.capture());
		assertThat(payload.getValue()).contains("2026-01-02T03:04:05Z");
	}

	private String encode(ChatTurn turn) {
		return this.json.writeValueAsString(turn);
	}

}
