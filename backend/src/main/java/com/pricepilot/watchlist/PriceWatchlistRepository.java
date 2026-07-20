package com.pricepilot.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceWatchlistRepository extends JpaRepository<PriceWatchlistEntity, UUID>, JpaSpecificationExecutor<PriceWatchlistEntity> {

    @Query("SELECT pw FROM PriceWatchlistEntity pw " +
           "JOIN FETCH pw.product p " +
           "WHERE pw.user.id = :userId")
    List<PriceWatchlistEntity> findAllByUserIdWithProduct(@Param("userId") UUID userId);

    @Query("SELECT pw FROM PriceWatchlistEntity pw " +
           "JOIN FETCH pw.product p " +
           "JOIN FETCH pw.user u " +
           "WHERE pw.id = :id")
    Optional<PriceWatchlistEntity> findByIdWithRelations(@Param("id") UUID id);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<PriceWatchlistEntity> findByUserIdAndProductId(UUID userId, UUID productId);

    @Query("SELECT COUNT(pw) FROM PriceWatchlistEntity pw WHERE pw.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(pw) FROM PriceWatchlistEntity pw WHERE pw.user.id = :userId AND pw.active = :active")
    long countByUserIdAndActive(@Param("userId") UUID userId, @Param("active") boolean active);

    @Query("SELECT pw FROM PriceWatchlistEntity pw JOIN FETCH pw.product p JOIN FETCH pw.user u WHERE pw.active = true AND pw.currentBestPrice <= pw.targetPrice")
    List<PriceWatchlistEntity> findAllTriggeredWatchlists();

    @Query("SELECT pw FROM PriceWatchlistEntity pw JOIN FETCH pw.product p WHERE pw.active = true")
    List<PriceWatchlistEntity> findAllActiveWatchlists();
}

