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
}


