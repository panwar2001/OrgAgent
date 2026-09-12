package com.panwar2001.orgagent.features.chat.llm;

/**
 * An answer produced by the language model.
 *
 * @param text the answer text
 * @param model the model that produced it, when the provider reports one
 */
public record GeneratedAnswer(String text, String model) {
}
