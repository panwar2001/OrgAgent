package com.panwar2001.orgagent.features.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.config.OrgAgentProperties;
import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.redis.ChatTurn;
import com.panwar2001.orgagent.core.redis.ChatWindowStore;
import com.panwar2001.orgagent.core.redis.RedisKeys;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.chat.cache.SemanticAnswerCache;
import com.panwar2001.orgagent.features.chat.dto.AskQuestionRequest;
import com.panwar2001.orgagent.features.chat.dto.ChatAnswerResponse;
import com.panwar2001.orgagent.features.chat.dto.ChatMessageResponse;
import com.panwar2001.orgagent.features.chat.dto.ChatSource;
import com.panwar2001.orgagent.features.chat.dto.ConversationResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationSummaryResponse;
import com.panwar2001.orgagent.features.chat.dto.ConversationWindowResponse;
import com.panwar2001.orgagent.features.chat.llm.AnswerGenerator;
import com.panwar2001.orgagent.features.chat.llm.GeneratedAnswer;
import com.panwar2001.orgagent.features.chat.rag.RagPromptBuilder;
import com.panwar2001.orgagent.features.chat.rag.RagRetriever;
import com.panwar2001.orgagent.features.chat.rag.RetrievedChunk;
import com.panwar2001.orgagent.features.organization.OrganizationService;
import com.panwar2001.orgagent.features.project.Project;
import com.panwar2001.orgagent.features.project.ProjectService;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Answers questions about a project's documents.
 *
 * <p>One turn is:
 * <ol>
 * <li>read the recent conversation out of Redis — the live window, no database round trip;</li>
 * <li>ask the semantic cache. A hit returns immediately and still records the turn;</li>
 * <li>otherwise embed the question and pull the closest passages out of pgvector, scoped to the
 * project;</li>
 * <li>send instructions + context + window + question to the model;</li>
 * <li>hand the Redis window, the Postgres log and the cache to asynchronous writers.</li>
 * </ol>
 */
@Slf4j
@Service
public class ChatService {

	private final OrganizationService organizationService;

	private final ProjectService projectService;

	private final ChatConversationRepository conversations;

	private final ChatMessageRepository messages;

	private final ChatWindowStore windowStore;

	private final RagRetriever retriever;

	private final RagPromptBuilder promptBuilder;

	private final AnswerGenerator answerGenerator;

	private final SemanticAnswerCache answerCache;

	private final ChatPersistenceService persistence;

	private final OrgAgentProperties properties;

	public ChatService(OrganizationService organizationService,
			ProjectService projectService,
			ChatConversationRepository conversations,
			ChatMessageRepository messages,
			ChatWindowStore windowStore,
			RagRetriever retriever,
			RagPromptBuilder promptBuilder,
			AnswerGenerator answerGenerator,
			SemanticAnswerCache answerCache,
			ChatPersistenceService persistence,
			OrgAgentProperties properties) {
		this.organizationService = organizationService;
		this.projectService = projectService;
		this.conversations = conversations;
		this.messages = messages;
		this.windowStore = windowStore;
		this.retriever = retriever;
		this.promptBuilder = promptBuilder;
		this.answerGenerator = answerGenerator;
		this.answerCache = answerCache;
		this.persistence = persistence;
		this.properties = properties;
	}

	/**
	 * Asks a question.
	 *
	 * @param organizationId owning organization
	 * @param projectId project whose documents answer the question
	 * @param request the question and, optionally, the conversation to continue
	 * @return the answer together with the conversation id and the passages it used
	 */
	public ChatAnswerResponse ask(UUID organizationId, UUID projectId, AskQuestionRequest request) {
		requireActiveProject(organizationId, projectId);

		ChatConversation conversation = resolveConversation(organizationId, projectId, request);
		String windowKey = RedisKeys.chatWindow(organizationId.toString(), projectId.toString(),
				conversation.getId().toString());
		String question = request.question().strip();

		// 1. Live history comes from Redis, not from the database.
		List<ChatTurn> history = this.windowStore.recent(windowKey, this.properties.rag().chatWindowSize());

		Instant askedAt = Instant.now();

		// 2. A semantically equivalent question that was already answered never reaches the model.
		Optional<String> cached = this.answerCache.find(organizationId, projectId, question);
		if (cached.isPresent()) {
			log.debug("Answering conversation {} from the semantic cache", conversation.getId());
			ChatAnswerResponse response = new ChatAnswerResponse(conversation.getId(), cached.get(), true, List.of(),
					null, null, Instant.now());
			record(conversation, windowKey, question, response, askedAt);
			return response;
		}

		// 3. Retrieval-augmented context.
		List<RetrievedChunk> context = this.retriever.retrieve(projectId, question);

		// 4. The model call.
		Prompt prompt = this.promptBuilder.build(question, history, context);
		long startedAt = System.nanoTime();
		GeneratedAnswer generated = this.answerGenerator.generate(prompt);
		long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;

		ChatAnswerResponse response = new ChatAnswerResponse(conversation.getId(), generated.text(), false,
				context.stream().map(ChatSource::from).toList(), generated.model(), latencyMs, Instant.now());

		// 5. Side effects are written asynchronously; the user already has the answer.
		record(conversation, windowKey, question, response, askedAt);
		this.persistence.cacheAnswer(organizationId, projectId, question, generated.text());
		return response;
	}

	/** The live window of a conversation, read straight from Redis. */
	public ConversationWindowResponse window(UUID organizationId, UUID projectId, UUID conversationId) {
		requireConversation(organizationId, projectId, conversationId);
		String windowKey = RedisKeys.chatWindow(organizationId.toString(), projectId.toString(),
				conversationId.toString());
		return ConversationWindowResponse.of(conversationId,
				this.windowStore.recent(windowKey, this.properties.rag().chatWindowSize()));
	}

	/** The permanent log of a conversation, oldest turn first. */
	public PageResponse<ChatMessageResponse> history(UUID organizationId, UUID projectId, UUID conversationId,
			Pageable pageable) {
		requireConversation(organizationId, projectId, conversationId);
		Page<ChatMessage> page = this.messages.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
		return PageResponse.from(page, ChatMessageResponse::from);
	}

	/**
	 * Every conversation of an organization, across all its projects, most recently used first.
	 *
	 * <p>This is what the console's session rail lists.
	 */
	public PageResponse<ConversationSummaryResponse> organizationConversations(UUID organizationId,
			Pageable pageable) {
		this.organizationService.require(organizationId);
		Page<ChatConversation> page = this.conversations.findByOrganizationIdOrderByUpdatedAtDesc(organizationId,
				pageable);
		Map<UUID, String> projectNames = this.projectService.namesById(page.getContent()
			.stream()
			.map(ChatConversation::getProjectId)
			.distinct()
			.toList());
		return PageResponse.from(page,
				conversation -> ConversationSummaryResponse.from(conversation,
						projectNames.getOrDefault(conversation.getProjectId(), "Unknown project")));
	}

	/** The conversations of a project, most recently used first. */
	public PageResponse<ConversationResponse> conversations(UUID organizationId, UUID projectId, Pageable pageable) {
		this.projectService.require(organizationId, projectId);
		Page<ChatConversation> page = this.conversations
			.findByOrganizationIdAndProjectIdOrderByUpdatedAtDesc(organizationId, projectId, pageable);
		return PageResponse.from(page, ConversationResponse::from);
	}

	/** Drops a conversation: its live window, its log and the row itself. */
	public void delete(UUID organizationId, UUID projectId, UUID conversationId) {
		ChatConversation conversation = requireConversation(organizationId, projectId, conversationId);
		this.windowStore.clear(
				RedisKeys.chatWindow(organizationId.toString(), projectId.toString(), conversationId.toString()));
		this.conversations.delete(conversation);
	}

	ChatConversation requireConversation(UUID organizationId, UUID projectId, UUID conversationId) {
		return this.conversations
			.findByIdAndOrganizationIdAndProjectId(conversationId, organizationId, projectId)
			.orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.CONVERSATION_NOT_FOUND, conversationId));
	}

	private void record(ChatConversation conversation, String windowKey, String question, ChatAnswerResponse response,
			Instant askedAt) {
		// The answer is always logged after the question, even if both are stamped in the same
		// microsecond, so the log reads in the order things were said.
		Instant answeredAt = response.answeredAt().isAfter(askedAt) ? response.answeredAt()
				: askedAt.plusNanos(1_000);

		this.persistence.appendToWindow(windowKey, new ChatTurn(com.panwar2001.orgagent.core.redis.ChatRole.USER,
				question, askedAt),
				new ChatTurn(com.panwar2001.orgagent.core.redis.ChatRole.ASSISTANT, response.answer(), answeredAt));

		this.persistence.persistTurn(
				ChatMessage.question(conversation.getId(), conversation.getOrganizationId(), conversation.getProjectId(),
						question, askedAt),
				ChatMessage.answer(conversation.getId(), conversation.getOrganizationId(),
						conversation.getProjectId(), response.answer(), response.fromCache(), response.model(),
						response.latencyMs(), answeredAt));
	}

	private ChatConversation resolveConversation(UUID organizationId, UUID projectId, AskQuestionRequest request) {
		if (request.conversationId() == null) {
			return this.conversations.save(ChatConversation.start(organizationId, projectId, request.question()));
		}
		return requireConversation(organizationId, projectId, request.conversationId());
	}

	private Project requireActiveProject(UUID organizationId, UUID projectId) {
		Project project = this.projectService.require(organizationId, projectId);
		if (!project.isActive()) {
			throw new ConflictException(ErrorCode.CONFLICT,
					"Project '%s' is archived and no longer answers questions".formatted(project.getSlug()));
		}
		return project;
	}

	/** Exposed for diagnostics and future rate limiting. */
	public long countMessages(UUID conversationId) {
		return this.messages.countByConversationId(conversationId);
	}

}
