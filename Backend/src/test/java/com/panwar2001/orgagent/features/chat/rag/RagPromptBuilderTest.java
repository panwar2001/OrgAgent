package com.panwar2001.orgagent.features.chat.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.panwar2001.orgagent.core.redis.ChatRole;
import com.panwar2001.orgagent.core.redis.ChatTurn;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class RagPromptBuilderTest {

	private final RagPromptBuilder builder = new RagPromptBuilder();

	@Test
	void startsWithInstructionsAndTheRetrievedContext() {
		Prompt prompt = builder.build("How long do refunds take?",
				List.of(),
				List.of(new RetrievedChunk("doc-1", "Refund policy", "Refunds take five working days.", 0.91),
						new RetrievedChunk("doc-2", "Employee handbook", "Full-time is 40 hours.", 0.72)));

		Message system = prompt.getInstructions().get(0);
		assertThat(system.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(system.getText()).contains("using only the").contains("Project context:")
			.contains("[1] Refund policy")
			.contains("Refunds take five working days.")
			.contains("[2] Employee handbook");
	}

	@Test
	void saysSoWhenNothingWasRetrievedInsteadOfPretendingThereIsContext() {
		Prompt prompt = builder.build("How long do refunds take?", List.of(), List.of());

		assertThat(prompt.getInstructions().get(0).getText()).contains(RagPromptBuilder.NO_CONTEXT_NOTICE);
	}

	@Test
	void replaysTheConversationWindowInOrderBeforeTheNewQuestion() {
		List<ChatTurn> history = List.of(new ChatTurn(ChatRole.USER, "What is the refund window?", at(1)),
				new ChatTurn(ChatRole.ASSISTANT, "Five working days.", at(2)));

		Prompt prompt = builder.build("And for gift cards?", history, List.of());

		List<Message> messages = prompt.getInstructions();
		assertThat(messages).hasSize(4);
		assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1).getText()).isEqualTo("What is the refund window?");
		assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
		assertThat(messages.get(2).getText()).isEqualTo("Five working days.");
		assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(3).getText()).isEqualTo("And for gift cards?");
	}

	@Test
	void keepsInjectedSystemTurnsInTheirPlace() {
		List<ChatTurn> history = List.of(new ChatTurn(ChatRole.SYSTEM, "department: HR", at(1)),
				new ChatTurn(ChatRole.USER, "hello", at(2)));

		Prompt prompt = builder.build("question", history, List.of());

		assertThat(prompt.getInstructions().get(1)).isInstanceOf(SystemMessage.class);
	}

	@Test
	void numbersEveryPassageSoTheAnswerCanCiteIt() {
		String context = builder.renderContext(List.of(new RetrievedChunk("d", "A", "first", 0.5),
				new RetrievedChunk("d", "B", "second", 0.5), new RetrievedChunk("d", "C", "third", 0.5)));

		assertThat(context).contains("[1] A").contains("[2] B").contains("[3] C");
	}

	@Test
	void reportsWhetherThereIsAConversationToContinue() {
		assertThat(builder.hasHistory(List.of())).isFalse();
		assertThat(builder.hasHistory(List.of(new ChatTurn(ChatRole.SYSTEM, "injected", at(1))))).isFalse();
		assertThat(builder.hasHistory(List.of(new ChatTurn(ChatRole.USER, "hi", at(1))))).isTrue();
	}

	private Instant at(int second) {
		return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second);
	}

}
