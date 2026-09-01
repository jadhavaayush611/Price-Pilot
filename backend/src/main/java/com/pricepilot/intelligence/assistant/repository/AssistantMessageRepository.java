package com.pricepilot.intelligence.assistant.repository;

import com.pricepilot.intelligence.assistant.model.AssistantMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssistantMessageRepository extends JpaRepository<AssistantMessageEntity, UUID> {

    @Query("SELECT m FROM AssistantMessageEntity m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt ASC")
    List<AssistantMessageEntity> findAllByConversationIdOrderByCreatedAtAsc(@Param("conversationId") UUID conversationId);
}
