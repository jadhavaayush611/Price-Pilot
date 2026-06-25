package com.pricepilot.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import com.pricepilot.interaction.dto.UserInteractionEventResponseDTO;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String role;

    private long savedCount;
    private long watchlistCount;
    private long totalActivitiesCount;
    private long activePriceAlertsCount;

    private List<ProductResponseDTO> recommendations;
    private List<ProductResponseDTO> recentlyViewed;
    private List<WatchlistResponseDTO> priceDropAlerts;
    private List<ProductResponseDTO> trendingProducts;
    private List<WatchlistResponseDTO> watchlists;
    private List<SavedProductResponseDTO> savedProducts;
    private List<UserInteractionEventResponseDTO> recentActivity;
    private List<String> recentSearches;
    private List<Map<String, Object>> mostClickedSellers;
}
