package com.panwar2001.orgagent.core.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;

import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * The live conversation window: the last N turns of a chat, kept in Redis so answering a follow-up
 * never needs a database round trip.
 *
 * <p>Turns are stored newest-first as a Redis list (cheap {@code LPUSH} + {@code LTRIM}), so
 * trimming to the window size costs nothing and the oldest turns age out on their own. A TTL keeps
 * abandoned conversations from occupying memory forever.
 *
 * <p>Postgres remains the system of record: this window is a cache, and losing it degrades context
 * rather than correctness.
 */
@Slf4j
@Component
public class ChatWindowStore {

	private final StringRedisTemplate redis;
	private final ObjectMapper json;
	private final int windowSize;
	private final Duration ttl;

	public ChatWindowStore(StringRedisTemplate redis, ObjectMapper json, OrgAgentProperties properties) {
		this.redis = redis;
		this.json = json;
		this.windowSize = properties.rag().chatWindowSize();
		this.ttl = properties.rag().chatWindowTtl();
	}

	/**
	 * Adds a turn to the window, keeping only the newest {@code windowSize} turns.
	 *
	 * @param windowKey key built by {@link RedisKeys#chatWindow}
	 * @param turn the turn to record
	 */
	public void append(String windowKey, ChatTurn turn) {
		ListOperations<String, String> list = this.redis.opsForList();
		list.leftPush(windowKey, this.json.writeValueAsString(turn));
		list.trim(windowKey, 0, this.windowSize - 1L);
		this.redis.expire(windowKey, this.ttl);
	}

	/** Every turn currently in the window, oldest first, ready to be replayed into a prompt. */
	public List<ChatTurn> recent(String windowKey) {
		return recent(windowKey, this.windowSize);
	}

	/**
	 * The newest {@code limit} turns, oldest first.
	 *
	 * @param windowKey key built by {@link RedisKeys#chatWindow}
	 * @param limit how many turns to read; clamped to the configured window size
	 */
	public List<ChatTurn> recent(String windowKey, int limit) {
		int bounded = Math.max(1, Math.min(limit, this.windowSize));
		List<String> payloads = this.redis.opsForList().range(windowKey, 0, bounded - 1L);
		if (payloads == null || payloads.isEmpty()) {
			return List.of();
		}

		List<ChatTurn> newestFirst = new ArrayList<>(payloads.size());
		for (String payload : payloads) {
			readTurn(payload).ifPresent(newestFirst::add);
		}
		// Stored newest-first; prompts read oldest-first.
		return List.copyOf(newestFirst.reversed());
	}

	/** Number of turns currently held, without reading them. */
	public long size(String windowKey) {
		Long size = this.redis.opsForList().size(windowKey);
		return size == null ? 0L : size;
	}

	/** Drops the window, e.g. when a conversation is deleted. */
	public void clear(String windowKey) {
		this.redis.delete(windowKey);
	}

	private Optional<ChatTurn> readTurn(String payload) {
		try {
			return Optional.ofNullable(this.json.readValue(payload, ChatTurn.class));
		}
		catch (RuntimeException ex) {
			// A single unreadable entry must not take the whole conversation down.
			log.warn("Skipping unreadable chat turn from the Redis window: {}", ex.getMessage());
			return Optional.empty();
		}
	}

}
