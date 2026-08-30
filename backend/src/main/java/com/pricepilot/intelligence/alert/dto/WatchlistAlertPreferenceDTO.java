package com.pricepilot.intelligence.alert.dto;

import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistAlertPreferenceDTO {

    private UUID id;
    private UUID watchlistId;
    private boolean enabled;
    private boolean priceDropEnabled;
    private BigDecimal priceDropPercentage;
    private boolean targetPriceEnabled;
    private boolean historicalLowEnabled;
    private boolean goodDealEnabled;
    private boolean backInStockEnabled;
    private boolean priceIncreaseEnabled;

    public static WatchlistAlertPreferenceDTO fromEntity(WatchlistAlertPreferenceEntity entity) {
        if (entity == null) {
            return null;
        }

        return WatchlistAlertPreferenceDTO.builder()
                .id(entity.getId())
                .watchlistId(entity.getWatchlist() != null ? entity.getWatchlist().getId() : null)
                .enabled(entity.isEnabled())
                .priceDropEnabled(entity.isPriceDropEnabled())
                .priceDropPercentage(entity.getPriceDropPercentage())
                .targetPriceEnabled(entity.isTargetPriceEnabled())
                .historicalLowEnabled(entity.isHistoricalLowEnabled())
                .goodDealEnabled(entity.isGoodDealEnabled())
                .backInStockEnabled(entity.isBackInStockEnabled())
                .priceIncreaseEnabled(entity.isPriceIncreaseEnabled())
                .build();
    }
}
