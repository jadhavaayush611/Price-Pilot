package com.pricepilot.productprice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPriceEntity, UUID> {
    
    // We can retrieve product prices with joins to product and seller
    @Query("SELECT pp FROM ProductPriceEntity pp " +
           "JOIN FETCH pp.product p " +
           "JOIN FETCH pp.seller s " +
           "WHERE (:search IS NULL OR " +
           "      LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "      LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ProductPriceEntity> findAllWithRelations(@Param("search") String search, Pageable pageable);

    @Query("SELECT pp FROM ProductPriceEntity pp " +
           "JOIN FETCH pp.seller s " +
           "WHERE pp.product.id IN :productIds")
    java.util.List<ProductPriceEntity> findPricesWithSellersByProductIds(@Param("productIds") java.util.List<UUID> productIds);
}
