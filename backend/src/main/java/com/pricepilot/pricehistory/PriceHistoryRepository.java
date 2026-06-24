package com.pricepilot.pricehistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, UUID> {

    @Query(value = "SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s",
            countQuery = "SELECT COUNT(ph) FROM PriceHistoryEntity ph")
    Page<PriceHistoryEntity> findAllWithRelations(Pageable pageable);

    @Query(value = "SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s " +
            "WHERE ph.product.id = :productId",
            countQuery = "SELECT COUNT(ph) FROM PriceHistoryEntity ph WHERE ph.product.id = :productId")
    Page<PriceHistoryEntity> findByProductIdWithRelations(@Param("productId") UUID productId, Pageable pageable);

    @Query(value = "SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s " +
            "WHERE ph.seller.id = :sellerId",
            countQuery = "SELECT COUNT(ph) FROM PriceHistoryEntity ph WHERE ph.seller.id = :sellerId")
    Page<PriceHistoryEntity> findBySellerIdWithRelations(@Param("sellerId") UUID sellerId, Pageable pageable);

    @Query("SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s " +
            "WHERE ph.changePercentage < 0 " +
            "ORDER BY ph.changePercentage ASC")
    List<PriceHistoryEntity> findLargestPriceDrops(Pageable pageable);

    @Query("SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s " +
            "WHERE ph.changePercentage > 0 " +
            "ORDER BY ph.changePercentage DESC")
    List<PriceHistoryEntity> findLargestPriceIncreases(Pageable pageable);

    @Query("SELECT ph FROM PriceHistoryEntity ph " +
            "JOIN FETCH ph.product p " +
            "JOIN FETCH ph.seller s " +
            "ORDER BY ph.changedAt DESC")
    List<PriceHistoryEntity> findRecentPriceChanges(Pageable pageable);
}
