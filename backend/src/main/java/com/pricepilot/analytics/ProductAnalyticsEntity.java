package com.pricepilot.analytics;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.product.ProductEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_analytics", indexes = {
    @Index(name = "idx_product_analytics_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAnalyticsEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_product_analytics_product"))
    private ProductEntity product;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    @Column(name = "save_count", nullable = false)
    @Builder.Default
    private long saveCount = 0L;

    @Column(name = "watchlist_count", nullable = false)
    @Builder.Default
    private long watchlistCount = 0L;

    @Column(name = "price_change_count", nullable = false)
    @Builder.Default
    private long priceChangeCount = 0L;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;
}
