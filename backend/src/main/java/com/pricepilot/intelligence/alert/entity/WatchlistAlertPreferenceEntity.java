package com.pricepilot.intelligence.alert.entity;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "watchlist_alert_preferences",
    indexes = {
        @Index(name = "idx_watchlist_alert_prefs_watchlist", columnList = "watchlist_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistAlertPreferenceEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_watchlist_alert_prefs_watchlist"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private PriceWatchlistEntity watchlist;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "price_drop_enabled", nullable = false)
    @Builder.Default
    private boolean priceDropEnabled = true;

    @Column(name = "price_drop_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal priceDropPercentage = BigDecimal.valueOf(10.00);

    @Column(name = "target_price_enabled", nullable = false)
    @Builder.Default
    private boolean targetPriceEnabled = true;

    @Column(name = "historical_low_enabled", nullable = false)
    @Builder.Default
    private boolean historicalLowEnabled = true;

    @Column(name = "good_deal_enabled", nullable = false)
    @Builder.Default
    private boolean goodDealEnabled = true;

    @Column(name = "back_in_stock_enabled", nullable = false)
    @Builder.Default
    private boolean backInStockEnabled = true;

    @Column(name = "price_increase_enabled", nullable = false)
    @Builder.Default
    private boolean priceIncreaseEnabled = false;

    // Stateful notification tracking to eliminate duplicate alert spam
    @Column(name = "last_notified_price", precision = 10, scale = 2)
    private BigDecimal lastNotifiedPrice;

    @Column(name = "last_notified_target_price", precision = 10, scale = 2)
    private BigDecimal lastNotifiedTargetPrice;

    @Column(name = "last_notified_historical_low", precision = 10, scale = 2)
    private BigDecimal lastNotifiedHistoricalLow;

    @Column(name = "last_notified_deal_quality", length = 50)
    private String lastNotifiedDealQuality;

    @Column(name = "last_notified_in_stock")
    private Boolean lastNotifiedInStock;

    public static WatchlistAlertPreferenceEntity defaultForWatchlist(PriceWatchlistEntity watchlist) {
        return WatchlistAlertPreferenceEntity.builder()
                .watchlist(watchlist)
                .enabled(true)
                .priceDropEnabled(true)
                .priceDropPercentage(BigDecimal.valueOf(10.00))
                .targetPriceEnabled(true)
                .historicalLowEnabled(true)
                .goodDealEnabled(true)
                .backInStockEnabled(true)
                .priceIncreaseEnabled(false)
                .build();
    }
}
