package com.pricepilot.intelligence.dashboard.dto;

import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Root response DTO for PricePilot v1.1 Dashboard V2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardV2ResponseDTO implements Serializable {

    private DashboardOverviewDTO overview;
    private List<AttentionItemDTO> attentionItems;
    private List<PriceOpportunityDTO> priceOpportunities;
    private List<WatchedProductCardDTO> watchedProducts;
    private List<PriceAlertResponseDTO> recentAlerts;
    private DashboardRecommendationsDTO recommendations;
    private List<RecentActivityDTO> recentActivity;
    private LocalDateTime generatedAt;
}
