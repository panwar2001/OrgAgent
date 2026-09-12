package com.panwar2001.orgagent.features.chat.llm;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * Produces an answer for a prompt.
 *
 * <p>The orchestration depends on this narrow interface rather than on Spring AI's model types, so
 * the chat pipeline can be tested without a provider and a different model can be swapped in.
 */
public interface AnswerGenerator {

	/**
	 * @param prompt instructions, context, history and the question
	 * @return the generated answer
	 */
	GeneratedAnswer generate(Prompt prompt);

}
