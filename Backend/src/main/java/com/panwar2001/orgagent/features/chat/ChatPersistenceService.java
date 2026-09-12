package com.panwar2001.orgagent.features.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.redis.ChatTurn;
import com.panwar2001.orgagent.core.redis.ChatWindowStore;
import com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The writes that happen after the user already has their answer.
 *
 * <p>A chat turn produces three side effects — the live Redis window, the Postgres integration log,
 * and the semantic cache — and none of them is worth making the user wait for. Each runs on its own
 * virtual thread and swallows its own failures: if Redis is briefly unavailable the answer still
 * stands, and the Postgres log remains the system of record.
 */
@Slf4j
@Service
public class ChatPersistenceService {

	private final ChatWindowStore windowStore;

	private final ChatMessageRepository messages;

	private final ChatConversationRepository conversations;

	private final SemanticAnswerCache answerCache;

	public ChatPersistenceService(ChatWindowStore windowStore,
			ChatMessageRepository messages,
			ChatConversationRepository conversations,
			SemanticAnswerCache answerCache) {
		this.windowStore = windowStore;
		this.messages = messages;
		this.conversations = conversations;
		this.answerCache = answerCache;
	}

	/** Adds the question and the answer to the live window that the next prompt replays. */
	@Async
	public void appendToWindow(String windowKey, ChatTurn question, ChatTurn answer) {
		try {
			this.windowStore.append(windowKey, question);
			this.windowStore.append(windowKey, answer);
		}
		catch (RuntimeException ex) {
			log.error("Could not update the Redis chat window {}: {}", windowKey, ex.getMessage());
		}
	}

	/** Appends both turns to the permanent integration log. */
	@Async
	public void persistTurn(ChatMessage question, ChatMessage answer) {
		try {
			this.messages.saveAll(List.of(question, answer));
			this.conversations.touch(question.getConversationId(), Instant.now());
		}
		catch (RuntimeException ex) {
			log.error("Could not append to the chat history of conversation {}", question.getConversationId(), ex);
		}
	}

	/** Remembers the answer so an equivalent question can skip the model call. */
	@Async
	public void cacheAnswer(UUID organizationId, UUID projectId, String question, String answer) {
		try {
			this.answerCache.store(organizationId, projectId, question, answer);
		}
		catch (RuntimeException ex) {
			log.warn("Could not populate the semantic cache for project {}: {}", projectId, ex.getMessage());
		}
	}

}
