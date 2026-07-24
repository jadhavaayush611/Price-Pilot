package com.pricepilot.intelligence.comparison.repository;

import com.pricepilot.intelligence.comparison.entity.ComparisonSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing comparison session persistence.
 */
@Repository
public interface ComparisonSessionRepository extends JpaRepository<ComparisonSessionEntity, UUID> {

    Optional<ComparisonSessionEntity> findBySessionToken(String sessionToken);

    List<ComparisonSessionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
