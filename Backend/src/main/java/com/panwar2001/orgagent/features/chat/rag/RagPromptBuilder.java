package com.panwar2001.orgagent.features.chat.rag;

import java.util.ArrayList;
import java.util.List;

import com.panwar2001.orgagent.core.redis.ChatRole;
import com.panwar2001.orgagent.core.redis.ChatTurn;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Builds the payload sent to the language model: instructions, the retrieved context, the recent
 * conversation window, and finally the new question.
 *
 * <p>Keeping this separate from the model call makes the prompt itself reviewable and testable —
 * which is where most answer-quality bugs actually live.
 */
@Component
public class RagPromptBuilder {

	public static final String NO_CONTEXT_NOTICE = "No passage of the project's documents matched this question.";

	private static final String SYSTEM_INSTRUCTIONS = """
			You are the internal assistant of an organization. Answer the user's question using only the
			project context below and the conversation so far.

			Rules:
			- Treat the context as the only source of truth. If it does not contain the answer, say so
			  plainly and suggest what the user could ask instead.
			- Never invent policies, numbers, names or links.
			- Answer in the language the question was asked in.
			- Be concise, and cite the document titles you used.""";

	/**
	 * @param question the new question
	 * @param history previous turns of the conversation, oldest first
	 * @param context passages retrieved from the project's documents
	 * @return the prompt to send
	 */
	public Prompt build(String question, List<ChatTurn> history, List<RetrievedChunk> context) {
		List<Message> messages = new ArrayList<>(history.size() + 2);
		messages.add(new SystemMessage(SYSTEM_INSTRUCTIONS + "\n\n" + renderContext(context)));
		for (ChatTurn turn : history) {
			messages.add(toMessage(turn));
		}
		messages.add(new UserMessage(question));
		return new Prompt(messages);
	}

	/** The context block, with each passage numbered and attributed to its document. */
	public String renderContext(List<RetrievedChunk> context) {
		if (context == null || context.isEmpty()) {
			return "Project context:\n" + NO_CONTEXT_NOTICE;
		}

		StringBuilder builder = new StringBuilder("Project context:\n");
		for (int index = 0; index < context.size(); index++) {
			RetrievedChunk chunk = context.get(index);
			builder.append('\n')
				.append('[')
				.append(index + 1)
				.append("] ")
				.append(chunk.title())
				.append('\n')
				.append(chunk.text())
				.append('\n');
		}
		return builder.toString().stripTrailing();
	}

	private Message toMessage(ChatTurn turn) {
		return switch (turn.role()) {
			case USER -> new UserMessage(turn.content());
			case ASSISTANT -> new AssistantMessage(turn.content());
			case SYSTEM -> new SystemMessage(turn.content());
		};
	}

	/** True when the window contains at least one turn, i.e. the question is a follow-up. */
	public boolean hasHistory(List<ChatTurn> history) {
		return history != null && history.stream().anyMatch(turn -> turn.role() != ChatRole.SYSTEM);
	}

}
