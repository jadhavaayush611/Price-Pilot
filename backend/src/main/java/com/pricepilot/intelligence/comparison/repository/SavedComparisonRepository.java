package com.pricepilot.intelligence.comparison.repository;

import com.pricepilot.intelligence.comparison.entity.SavedComparisonEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing user saved comparison persistence.
 */
@Repository
public interface SavedComparisonRepository extends JpaRepository<SavedComparisonEntity, UUID> {

    List<SavedComparisonEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<SavedComparisonEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT s FROM SavedComparisonEntity s " +
           "WHERE s.userId = :userId " +
           "AND (:search IS NULL OR :search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.notes) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SavedComparisonEntity> findByUserIdAndSearch(
            @Param("userId") UUID userId,
            @Param("search") String search,
            Pageable pageable);

    Optional<SavedComparisonEntity> findByIdAndUserId(UUID id, UUID userId);
}
