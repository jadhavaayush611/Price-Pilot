package com.pricepilot.analytics.dto;

import java.util.UUID;

public interface ProductAnalyticsProjection {
    UUID getProductId();
    long getViewCount();
    long getSaveCount();
    long getWatchlistCount();
    long getPriceChangeCount();
}
