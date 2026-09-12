package com.panwar2001.orgagent.features.chat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

	Optional<ChatConversation> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

	Page<ChatConversation> findByOrganizationIdAndProjectIdOrderByUpdatedAtDesc(UUID organizationId, UUID projectId,
			Pageable pageable);

	/** Every conversation of an organization, across all of its projects, most recent first. */
	Page<ChatConversation> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId, Pageable pageable);

	long countByProjectId(UUID projectId);

	/**
	 * Marks a conversation as recently used without loading and merging the entity, so logging a turn
	 * cannot fail on optimistic locking when two questions arrive at once.
	 */
	@Modifying
	@Transactional
	@Query("update ChatConversation c set c.updatedAt = :at where c.id = :id")
	int touch(@Param("id") UUID id, @Param("at") Instant at);

}
