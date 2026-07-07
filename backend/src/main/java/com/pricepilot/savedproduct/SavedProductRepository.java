package com.pricepilot.savedproduct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedProductRepository extends JpaRepository<SavedProductEntity, SavedProductId>, JpaSpecificationExecutor<SavedProductEntity> {

    @Query("SELECT sp FROM SavedProductEntity sp " +
           "JOIN FETCH sp.product p " +
           "WHERE sp.id.userId = :userId " +
           "AND p.archived = false " +
           "ORDER BY sp.createdAt DESC")
    List<SavedProductEntity> findAllByUserIdWithProduct(@Param("userId") UUID userId);

    boolean existsById(SavedProductId id);

    @Query("SELECT COUNT(sp) FROM SavedProductEntity sp WHERE sp.id.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);
}

