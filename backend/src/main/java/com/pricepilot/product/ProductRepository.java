package com.pricepilot.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {
    Page<ProductEntity> findByNameContainingIgnoreCaseOrBrandContainingIgnoreCaseOrCategoryContainingIgnoreCase(
            String name, String brand, String category, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN FETCH p.productPrices pp " +
            "LEFT JOIN FETCH pp.seller " +
            "WHERE p.id = :id")
    java.util.Optional<ProductEntity> findByIdWithPricesAndSellers(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN p.productPrices pp " +
            "GROUP BY p.id " +
            "ORDER BY COUNT(pp.id) DESC")
    java.util.List<ProductEntity> findPopularProducts(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    java.util.List<ProductEntity> findFirstPage(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "WHERE p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.id < :id) " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    java.util.List<ProductEntity> findNextPage(
            @org.springframework.data.repository.query.Param("createdAt") java.time.LocalDateTime createdAt,
            @org.springframework.data.repository.query.Param("id") java.util.UUID id,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "WHERE p.createdAt > :createdAt OR (p.createdAt = :createdAt AND p.id > :id) " +
            "ORDER BY p.createdAt ASC, p.id ASC")
    java.util.List<ProductEntity> findPrevPage(
            @org.springframework.data.repository.query.Param("createdAt") java.time.LocalDateTime createdAt,
            @org.springframework.data.repository.query.Param("id") java.util.UUID id,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN ProductAnalyticsEntity pa ON pa.product.id = p.id " +
            "ORDER BY (COALESCE(pa.viewCount, 0) * 1 + COALESCE(pa.saveCount, 0) * 5 + COALESCE(pa.watchlistCount, 0) * 10 + COALESCE(pa.priceChangeCount, 0) * 2) DESC, p.id DESC")
    java.util.List<ProductEntity> findTrendingProducts(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN ProductAnalyticsEntity pa ON pa.product.id = p.id " +
            "ORDER BY COALESCE(pa.watchlistCount, 0) DESC, p.id DESC")
    java.util.List<ProductEntity> findMostWatchedProducts(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN ProductAnalyticsEntity pa ON pa.product.id = p.id " +
            "ORDER BY COALESCE(pa.saveCount, 0) DESC, p.id DESC")
    java.util.List<ProductEntity> findMostSavedProducts(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProductEntity p " +
            "JOIN p.priceHistories ph " +
            "WHERE ph.priceDifference < 0 " +
            "GROUP BY p " +
            "ORDER BY SUM(ph.priceDifference) ASC, p.id DESC")
    java.util.List<ProductEntity> findProductsWithBiggestDrops(org.springframework.data.domain.Pageable pageable);
}


