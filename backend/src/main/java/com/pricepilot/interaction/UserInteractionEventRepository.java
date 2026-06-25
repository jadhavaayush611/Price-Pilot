package com.pricepilot.interaction;

import com.pricepilot.interaction.dto.AnalyticsCountProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserInteractionEventRepository 
        extends JpaRepository<UserInteractionEventEntity, UUID>, JpaSpecificationExecutor<UserInteractionEventEntity> {

    @Query("SELECT p.category as name, COUNT(e) as count " +
           "FROM UserInteractionEventEntity e " +
           "JOIN e.product p " +
           "WHERE e.interactionType = com.pricepilot.interaction.InteractionType.PRODUCT_VIEW " +
           "GROUP BY p.category " +
           "ORDER BY count DESC")
    List<AnalyticsCountProjection> findMostViewedCategories(Pageable pageable);

    @Query("SELECT p.brand as name, COUNT(e) as count " +
           "FROM UserInteractionEventEntity e " +
           "JOIN e.product p " +
           "WHERE e.interactionType = com.pricepilot.interaction.InteractionType.PRODUCT_VIEW " +
           "AND p.brand IS NOT NULL " +
           "GROUP BY p.brand " +
           "ORDER BY count DESC")
    List<AnalyticsCountProjection> findMostViewedBrands(Pageable pageable);

    @Query("SELECT u.email as name, COUNT(e) as count " +
           "FROM UserInteractionEventEntity e " +
           "JOIN e.user u " +
           "GROUP BY u.email " +
           "ORDER BY count DESC")
    List<AnalyticsCountProjection> findMostActiveUsers(Pageable pageable);

    @Query("SELECT s.name as name, COUNT(e) as count " +
           "FROM UserInteractionEventEntity e " +
           "JOIN e.seller s " +
           "WHERE e.interactionType = com.pricepilot.interaction.InteractionType.SELLER_CLICK " +
           "GROUP BY s.name " +
           "ORDER BY count DESC")
    List<AnalyticsCountProjection> findMostClickedSellers(Pageable pageable);

    @Query("SELECT FUNCTION('jsonb_extract_path_text', e.metadata, 'keyword') as name, COUNT(e) as count " +
           "FROM UserInteractionEventEntity e " +
           "WHERE e.interactionType = com.pricepilot.interaction.InteractionType.SEARCH " +
           "AND FUNCTION('jsonb_extract_path_text', e.metadata, 'keyword') IS NOT NULL " +
           "AND FUNCTION('jsonb_extract_path_text', e.metadata, 'keyword') <> '' " +
           "GROUP BY FUNCTION('jsonb_extract_path_text', e.metadata, 'keyword') " +
           "ORDER BY count DESC")
    List<AnalyticsCountProjection> findMostSearchedKeywords(Pageable pageable);

    @Query("SELECT e FROM UserInteractionEventEntity e " +
           "LEFT JOIN FETCH e.product p " +
           "LEFT JOIN FETCH e.seller s " +
           "WHERE e.user.id = :userId " +
           "ORDER BY e.createdAt DESC")
    List<UserInteractionEventEntity> findByUserIdWithRelations(@Param("userId") UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
