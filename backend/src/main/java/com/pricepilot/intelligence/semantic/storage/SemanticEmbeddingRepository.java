package com.pricepilot.intelligence.semantic.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for managing semantic vector embeddings.
 */
@Repository
public interface SemanticEmbeddingRepository extends JpaRepository<SemanticEmbeddingEntity, UUID> {

    Optional<SemanticEmbeddingEntity> findByEntityTypeAndEntityIdAndModelNameAndModelVersion(
            String entityType, String entityId, String modelName, String modelVersion
    );

    List<SemanticEmbeddingEntity> findByEntityTypeAndModelNameAndModelVersion(
            String entityType, String modelName, String modelVersion
    );

    List<SemanticEmbeddingEntity> findByModelNameAndModelVersion(
            String modelName, String modelVersion
    );

    boolean existsByEntityTypeAndEntityIdAndModelNameAndModelVersion(
            String entityType, String entityId, String modelName, String modelVersion
    );

    long countByModelNameAndModelVersion(String modelName, String modelVersion);

    @Modifying
    @Query("DELETE FROM SemanticEmbeddingEntity e WHERE e.entityType = :entityType AND e.entityId = :entityId")
    int deleteByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Modifying
    @Query("DELETE FROM SemanticEmbeddingEntity e WHERE e.entityType = :entityType AND e.entityId IN :entityIds")
    int deleteByEntityTypeAndEntityIdIn(@Param("entityType") String entityType, @Param("entityIds") Collection<String> entityIds);

    @Modifying
    @Query("DELETE FROM SemanticEmbeddingEntity e WHERE e.modelName = :modelName AND e.modelVersion = :modelVersion")
    int deleteByModelNameAndModelVersion(@Param("modelName") String modelName, @Param("modelVersion") String modelVersion);
}
