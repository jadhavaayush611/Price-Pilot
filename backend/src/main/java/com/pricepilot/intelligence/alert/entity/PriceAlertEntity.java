package com.pricepilot.intelligence.alert.entity;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts",
    indexes = {
        @Index(name = "idx_price_alerts_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_price_alerts_user_read", columnList = "user_id, read_at"),
        @Index(name = "idx_price_alerts_user_product", columnList = "user_id, product_id"),
        @Index(name = "idx_price_alerts_dedup", columnList = "deduplication_key", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlertEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_price_alerts_user"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_price_alerts_product"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", foreignKey = @ForeignKey(name = "fk_price_alerts_watchlist"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private PriceWatchlistEntity watchlist;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "trigger_value", precision = 10, scale = 2)
    private BigDecimal triggerValue;

    @Column(name = "observed_value", precision = 10, scale = 2)
    private BigDecimal observedValue;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 255)
    private String deduplicationKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public boolean isRead() {
        return readAt != null;
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
