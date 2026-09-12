package com.panwar2001.orgagent.core.redis;

import java.util.Objects;

/**
 * Every Redis key this service writes, built in one place.
 *
 * <p>Keys are namespaced {@code orgagent:...} and scoped by organization and project so a project's
 * live chat state can be inspected or dropped without touching another tenant's.
 */
public final class RedisKeys {

	public static final String NAMESPACE = "orgagent";

	private static final String CHAT_WINDOW_SUFFIX = "window";

	private RedisKeys() {
	}

	/**
	 * The rolling window of the most recent turns of one conversation.
	 *
	 * @param organizationId owning organization
	 * @param projectId project the conversation belongs to
	 * @param conversationId conversation identifier
	 * @return the Redis list key holding newest-first turns
	 */
	public static String chatWindow(String organizationId, String projectId, String conversationId) {
		Objects.requireNonNull(organizationId, "organizationId");
		Objects.requireNonNull(projectId, "projectId");
		Objects.requireNonNull(conversationId, "conversationId");
		return "%s:org:%s:project:%s:chat:%s:%s".formatted(NAMESPACE, organizationId, projectId, conversationId,
				CHAT_WINDOW_SUFFIX);
	}

	/** Pattern matching every live chat window, for operational inspection and cleanup. */
	public static String chatWindowPattern() {
		return "%s:org:*:project:*:chat:*:%s".formatted(NAMESPACE, CHAT_WINDOW_SUFFIX);
	}

	/** Pattern matching every live chat window of one project. */
	public static String projectChatWindowPattern(String organizationId, String projectId) {
		return "%s:org:%s:project:%s:chat:*:%s".formatted(NAMESPACE, organizationId, projectId, CHAT_WINDOW_SUFFIX);
	}

}
