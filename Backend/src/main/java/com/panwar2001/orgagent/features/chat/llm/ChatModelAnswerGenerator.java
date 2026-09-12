package com.panwar2001.orgagent.features.chat.llm;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.LlmException;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Calls the configured chat model.
 *
 * <p>Provider failures are translated into a 502 so the caller can tell "the model provider is
 * unavailable" from a bug in this service, and an empty completion is treated as a failure rather
 * than stored as an answer.
 */
@Component
@RequiredArgsConstructor
public class ChatModelAnswerGenerator implements AnswerGenerator {

	private final ChatModel chatModel;

	@Override
	public GeneratedAnswer generate(Prompt prompt) {
		ChatResponse response;
		try {
			response = this.chatModel.call(prompt);
		}
		catch (RuntimeException ex) {
			throw new LlmException("The language model call failed", ex);
		}

		String text = extractText(response);
		if (text == null || text.isBlank()) {
			throw new LlmException(ErrorCode.LLM_FAILED, "The language model returned an empty answer", null);
		}
		return new GeneratedAnswer(text.strip(), modelOf(response));
	}

	private String extractText(ChatResponse response) {
		if (response == null) {
			return null;
		}
		Generation generation = response.getResult();
		return generation == null || generation.getOutput() == null ? null : generation.getOutput().getText();
	}

	private String modelOf(ChatResponse response) {
		ChatResponseMetadata metadata = response.getMetadata();
		return metadata == null ? null : metadata.getModel();
	}

}
