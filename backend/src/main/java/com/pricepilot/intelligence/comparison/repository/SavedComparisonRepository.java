package com.pricepilot.intelligence.comparison.repository;

import com.pricepilot.intelligence.comparison.entity.SavedComparisonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing user saved comparison persistence.
 */
@Repository
public interface SavedComparisonRepository extends JpaRepository<SavedComparisonEntity, UUID> {

    List<SavedComparisonEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
