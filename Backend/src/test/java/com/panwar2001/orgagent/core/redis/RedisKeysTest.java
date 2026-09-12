package com.panwar2001.orgagent.core.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeysTest {

	@Test
	void scopesAWindowToOrganizationProjectAndConversation() {
		assertThat(RedisKeys.chatWindow("acme", "prj-1", "conv-9"))
			.isEqualTo("orgagent:org:acme:project:prj-1:chat:conv-9:window");
	}

	@Test
	void keepsTwoTenantsWindowsApart() {
		assertThat(RedisKeys.chatWindow("acme", "prj-1", "conv-9"))
			.isNotEqualTo(RedisKeys.chatWindow("globex", "prj-1", "conv-9"));
		assertThat(RedisKeys.chatWindow("acme", "prj-1", "conv-9"))
			.isNotEqualTo(RedisKeys.chatWindow("acme", "prj-2", "conv-9"));
	}

	@Test
	void exposesPatternsForInspectionAndCleanup() {
		assertThat(RedisKeys.chatWindowPattern()).isEqualTo("orgagent:org:*:project:*:chat:*:window");
		assertThat(RedisKeys.projectChatWindowPattern("acme", "prj-1"))
			.isEqualTo("orgagent:org:acme:project:prj-1:chat:*:window");
		assertThat(RedisKeys.chatWindow("acme", "prj-1", "conv-9")).startsWith(RedisKeys.NAMESPACE);
	}

	@Test
	void refusesToBuildAKeyWithoutAScope() {
		assertThatThrownBy(() -> RedisKeys.chatWindow(null, "prj-1", "conv-9"))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("organizationId");
	}

}
