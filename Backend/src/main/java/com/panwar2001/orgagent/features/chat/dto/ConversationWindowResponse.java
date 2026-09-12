package com.panwar2001.orgagent.features.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.redis.ChatTurn;

/**
 * The live window of a conversation, served from Redis.
 *
 * @param conversationId the conversation
 * @param turns the turns currently held, oldest first
 */
public record ConversationWindowResponse(UUID conversationId, List<Turn> turns) {

	/**
	 * One turn of the live window.
	 *
	 * @param role who produced the turn
	 * @param content the text
	 * @param at when it happened
	 */
	public record Turn(String role, String content, Instant at) {

		public static Turn from(ChatTurn turn) {
			return new Turn(turn.role().name(), turn.content(), turn.at());
		}

	}

	public static ConversationWindowResponse of(UUID conversationId, List<ChatTurn> turns) {
		return new ConversationWindowResponse(conversationId, turns.stream().map(Turn::from).toList());
	}

}
