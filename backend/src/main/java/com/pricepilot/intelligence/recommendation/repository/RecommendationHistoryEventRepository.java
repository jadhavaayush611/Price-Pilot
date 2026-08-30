package com.pricepilot.intelligence.recommendation.repository;

import com.pricepilot.intelligence.recommendation.entity.RecommendationHistoryEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationHistoryEventRepository extends JpaRepository<RecommendationHistoryEventEntity, UUID> {

    List<RecommendationHistoryEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<RecommendationHistoryEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<RecommendationHistoryEventEntity> findByRecommendedProductIdOrderByCreatedAtDesc(UUID productId);
}
