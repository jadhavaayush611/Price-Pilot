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
}

