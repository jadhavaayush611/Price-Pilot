package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Concise, actionable aggregate metrics for Dashboard V2 overview.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO implements Serializable {

    private int activeWatchlistsCount;
    private long unreadAlertsCount;
    private int historicalLowCount;
    private int goodOrExcellentDealCount;
    private int recentPriceDropCount;
    private long savedComparisonsCount;
    private long savedProductsCount;
}
