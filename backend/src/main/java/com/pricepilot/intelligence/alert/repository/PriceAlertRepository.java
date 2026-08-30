package com.pricepilot.intelligence.alert.repository;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlertEntity, UUID> {

    @Query("SELECT pa FROM PriceAlertEntity pa " +
           "JOIN FETCH pa.product p " +
           "WHERE pa.user.id = :userId " +
           "ORDER BY pa.createdAt DESC")
    Page<PriceAlertEntity> findAllByUserIdWithProduct(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT pa FROM PriceAlertEntity pa " +
           "JOIN FETCH pa.product p " +
           "WHERE pa.user.id = :userId AND pa.readAt IS NULL " +
           "ORDER BY pa.createdAt DESC")
    List<PriceAlertEntity> findUnreadByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(pa) FROM PriceAlertEntity pa WHERE pa.user.id = :userId AND pa.readAt IS NULL")
    long countUnreadByUserId(@Param("userId") UUID userId);

    boolean existsByDeduplicationKey(String deduplicationKey);

    Optional<PriceAlertEntity> findByDeduplicationKey(String deduplicationKey);

    @Modifying
    @Query("UPDATE PriceAlertEntity pa SET pa.readAt = :readAt WHERE pa.user.id = :userId AND pa.readAt IS NULL")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}
