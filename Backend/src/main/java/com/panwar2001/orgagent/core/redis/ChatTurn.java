package com.panwar2001.orgagent.core.redis;

import java.time.Instant;
import java.util.Objects;

/**
 * One turn of a live conversation as held by the Redis rolling window.
 *
 * @param role who produced the turn
 * @param content the text of the turn
 * @param at when the turn happened
 */
public record ChatTurn(ChatRole role, String content, Instant at) {

	public ChatTurn {
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(at, "at");
	}

	public static ChatTurn user(String content) {
		return new ChatTurn(ChatRole.USER, content, Instant.now());
	}

	public static ChatTurn assistant(String content) {
		return new ChatTurn(ChatRole.ASSISTANT, content, Instant.now());
	}

	public static ChatTurn system(String content) {
		return new ChatTurn(ChatRole.SYSTEM, content, Instant.now());
	}

}
