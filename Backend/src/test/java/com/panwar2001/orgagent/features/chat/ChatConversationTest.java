package com.panwar2001.orgagent.features.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatConversationTest {

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	@Test
	void isTitledAfterTheQuestionThatOpenedIt() {
		ChatConversation conversation = ChatConversation.start(this.organizationId, this.projectId,
				"  How long do refunds take?  ");

		assertThat(conversation.getId()).isNotNull();
		assertThat(conversation.getTitle()).isEqualTo("How long do refunds take?");
		assertThat(conversation.getOrganizationId()).isEqualTo(this.organizationId);
		assertThat(conversation.getProjectId()).isEqualTo(this.projectId);
	}

	@Test
	void collapsesWhitespaceInTheTitle() {
		assertThat(ChatConversation.start(this.organizationId, this.projectId, "how   long\n\ndo refunds take?")
			.getTitle()).isEqualTo("how long do refunds take?");
	}

	@Test
	void truncatesAVeryLongQuestion() {
		assertThat(ChatConversation.start(this.organizationId, this.projectId, "q".repeat(500)).getTitle())
			.hasSize(300)
			.endsWith("...");
	}

	@Test
	void fallsBackToAPlaceholderTitleForAnEmptyQuestion() {
		assertThat(ChatConversation.start(this.organizationId, this.projectId, "  ").getTitle())
			.isEqualTo("New conversation");
	}

	@Test
	void canBeRetitled() {
		ChatConversation conversation = ChatConversation.start(this.organizationId, this.projectId, "first");

		conversation.retitle("Refunds and returns");

		assertThat(conversation.getTitle()).isEqualTo("Refunds and returns");
	}

	@Test
	void knowsWhichProjectItBelongsTo() {
		ChatConversation conversation = ChatConversation.start(this.organizationId, this.projectId, "q");

		assertThat(conversation.belongsTo(this.organizationId, this.projectId)).isTrue();
		assertThat(conversation.belongsTo(UUID.randomUUID(), this.projectId)).isFalse();
		assertThat(conversation.belongsTo(this.organizationId, UUID.randomUUID())).isFalse();
	}

	@Test
	void stampsTimestampsWhenItIsPersisted() {
		ChatConversation conversation = ChatConversation.start(this.organizationId, this.projectId, "q");

		conversation.onInsert();

		assertThat(conversation.getCreatedAt()).isNotNull();
		assertThat(conversation.getUpdatedAt()).isEqualTo(conversation.getCreatedAt());
	}

}
