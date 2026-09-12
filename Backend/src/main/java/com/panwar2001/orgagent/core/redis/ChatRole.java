package com.panwar2001.orgagent.core.redis;

/** Who produced a turn of a conversation. */
public enum ChatRole {

	/** The question asked by the end user. */
	USER,

	/** The answer produced by the model. */
	ASSISTANT,

	/** Instructions or injected context that are not shown as a chat bubble. */
	SYSTEM

}
