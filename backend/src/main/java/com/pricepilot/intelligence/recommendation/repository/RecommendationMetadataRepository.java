package com.pricepilot.intelligence.recommendation.repository;

import com.pricepilot.intelligence.recommendation.entity.RecommendationMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing recommendation engine metadata.
 */
@Repository
public interface RecommendationMetadataRepository extends JpaRepository<RecommendationMetadataEntity, UUID> {

    List<RecommendationMetadataEntity> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
