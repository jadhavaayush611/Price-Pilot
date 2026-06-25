package com.pricepilot.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductAnalyticsRepository extends JpaRepository<ProductAnalyticsEntity, UUID> {

    @Query("SELECT pa FROM ProductAnalyticsEntity pa WHERE pa.product.id = :productId")
    Optional<ProductAnalyticsEntity> findByProductId(@Param("productId") UUID productId);

    @Query("SELECT pa.product.id as productId, pa.viewCount as viewCount, pa.saveCount as saveCount, " +
           "pa.watchlistCount as watchlistCount, pa.priceChangeCount as priceChangeCount " +
           "FROM ProductAnalyticsEntity pa WHERE pa.product.id = :productId")
    Optional<ProductAnalyticsProjection> findProjectionByProductId(@Param("productId") UUID productId);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.viewCount = pa.viewCount + 1, " +
           "    pa.lastViewedAt = :lastViewedAt, " +
           "    pa.updatedAt = :lastViewedAt " +
           "WHERE pa.product.id = :productId")
    int incrementViewCount(@Param("productId") UUID productId, @Param("lastViewedAt") LocalDateTime lastViewedAt);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.saveCount = pa.saveCount + 1, " +
           "    pa.updatedAt = :updatedAt " +
           "WHERE pa.product.id = :productId")
    int incrementSaveCount(@Param("productId") UUID productId, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.saveCount = CASE WHEN pa.saveCount > 0 THEN pa.saveCount - 1 ELSE 0 END, " +
           "    pa.updatedAt = :updatedAt " +
           "WHERE pa.product.id = :productId")
    int decrementSaveCount(@Param("productId") UUID productId, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.watchlistCount = pa.watchlistCount + 1, " +
           "    pa.updatedAt = :updatedAt " +
           "WHERE pa.product.id = :productId")
    int incrementWatchlistCount(@Param("productId") UUID productId, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.watchlistCount = CASE WHEN pa.watchlistCount > 0 THEN pa.watchlistCount - 1 ELSE 0 END, " +
           "    pa.updatedAt = :updatedAt " +
           "WHERE pa.product.id = :productId")
    int decrementWatchlistCount(@Param("productId") UUID productId, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ProductAnalyticsEntity pa " +
           "SET pa.priceChangeCount = pa.priceChangeCount + 1, " +
           "    pa.updatedAt = :updatedAt " +
           "WHERE pa.product.id = :productId")
    int incrementPriceChangeCount(@Param("productId") UUID productId, @Param("updatedAt") LocalDateTime updatedAt);
}
