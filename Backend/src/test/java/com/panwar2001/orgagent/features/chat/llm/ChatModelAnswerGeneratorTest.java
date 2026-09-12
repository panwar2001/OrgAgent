package com.panwar2001.orgagent.features.chat.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.LlmException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class ChatModelAnswerGeneratorTest {

	private final ChatModel chatModel = mock(ChatModel.class);

	private final ChatModelAnswerGenerator generator = new ChatModelAnswerGenerator(this.chatModel);

	@Test
	void returnsTheModelAnswerAndTheModelThatProducedIt() {
		when(this.chatModel.call(any(Prompt.class))).thenReturn(response("Five working days.", "gemini-2.5-flash"));

		GeneratedAnswer answer = generator.generate(new Prompt("How long do refunds take?"));

		assertThat(answer.text()).isEqualTo("Five working days.");
		assertThat(answer.model()).isEqualTo("gemini-2.5-flash");
	}

	@Test
	void trimsSurroundingWhitespace() {
		when(this.chatModel.call(any(Prompt.class))).thenReturn(response("  padded answer \n", "m"));

		assertThat(generator.generate(new Prompt("q")).text()).isEqualTo("padded answer");
	}

	@Test
	void treatsAnEmptyCompletionAsAFailureRatherThanAnAnswer() {
		when(this.chatModel.call(any(Prompt.class))).thenReturn(response("   ", "m"));

		assertThatThrownBy(() -> generator.generate(new Prompt("q"))).isInstanceOf(LlmException.class)
			.hasMessageContaining("empty answer")
			.satisfies(ex -> assertThat(((LlmException) ex).code()).isEqualTo(ErrorCode.LLM_FAILED));
	}

	@Test
	void reportsProviderFailuresAsBadGateway() {
		when(this.chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("quota exceeded"));

		assertThatThrownBy(() -> generator.generate(new Prompt("q"))).isInstanceOf(LlmException.class)
			.hasMessageContaining("failed")
			.satisfies(ex -> assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class));
	}

	@Test
	void copesWithAResponseWithoutAStatusMetadata() {
		when(this.chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("answer")))));

		GeneratedAnswer answer = generator.generate(new Prompt("q"));

		assertThat(answer.text()).isEqualTo("answer");
		assertThat(answer.model()).isNullOrEmpty();
	}

	private ChatResponse response(String text, String model) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.model(model)
			.usage(new DefaultUsage(10, 20))
			.build();
		return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(text))), metadata);
	}

}
