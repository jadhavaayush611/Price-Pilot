package com.pricepilot.intelligence.alert.repository;

import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistAlertPreferenceRepository extends JpaRepository<WatchlistAlertPreferenceEntity, UUID> {

    @Query("SELECT p FROM WatchlistAlertPreferenceEntity p WHERE p.watchlist.id = :watchlistId")
    Optional<WatchlistAlertPreferenceEntity> findByWatchlistId(@Param("watchlistId") UUID watchlistId);

    boolean existsByWatchlistId(UUID watchlistId);
}
