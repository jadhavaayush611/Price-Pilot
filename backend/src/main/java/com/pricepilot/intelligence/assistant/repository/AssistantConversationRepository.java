package com.pricepilot.intelligence.assistant.repository;

import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantConversationRepository extends JpaRepository<AssistantConversationEntity, UUID> {

    @Query("SELECT c FROM AssistantConversationEntity c WHERE c.user.id = :userId ORDER BY c.updatedAt DESC")
    List<AssistantConversationEntity> findAllByUserIdOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT c FROM AssistantConversationEntity c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.user.id = :userId")
    Optional<AssistantConversationEntity> findByIdAndUserIdWithMessages(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT c FROM AssistantConversationEntity c WHERE c.id = :id AND c.user.id = :userId")
    Optional<AssistantConversationEntity> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
