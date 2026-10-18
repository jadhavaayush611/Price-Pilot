package com.pricepilot.intelligence.marketplace.adapter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic offline mock adapter for tests and demonstrations.
 * Simulates third-party merchant offers offline without external network calls.
 * All results are explicitly identified as simulated data and never labeled as live or verified.
 */
@Component
public class MockMarketplaceOfferAdapter implements MarketplaceOfferAdapter {

    public static final String PROVIDER_ID = "mock";
    public static final String DISPLAY_NAME = "Simulated Marketplace Provider";

    private boolean enabled = true;

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
        if (!enabled || context == null) {
            return Collections.emptyList();
        }

        String query = context.productName() != null ? context.productName().toLowerCase() : "";
        List<RawMarketplaceOffer> offers = new ArrayList<>();

        if (query.contains("iphone") || query.contains("phone")) {
            offers.add(new RawMarketplaceOffer(
                    "mock-apple-store-1",
                    "Apple Authorized Outlet",
                    "https://mock-apple.example.com",
                    "https://mock-apple.example.com/item/101",
                    new BigDecimal("999.00"),
                    new BigDecimal("1099.00"),
                    "USD",
                    true,
                    15,
                    LocalDateTime.now().minusHours(3)
            ));
            offers.add(new RawMarketplaceOffer(
                    "mock-electro-hub-2",
                    "ElectroHub Direct",
                    "https://mock-electrohub.example.com",
                    "https://mock-electrohub.example.com/p/202",
                    new BigDecimal("979.99"),
                    new BigDecimal("1099.00"),
                    "USD",
                    true,
                    5,
                    LocalDateTime.now().minusHours(12)
            ));
        } else if (query.contains("macbook") || query.contains("laptop")) {
            offers.add(new RawMarketplaceOffer(
                    "mock-comp-depot-1",
                    "Computer Depot",
                    "https://mock-compdepot.example.com",
                    "https://mock-compdepot.example.com/sku/303",
                    new BigDecimal("1049.00"),
                    new BigDecimal("1199.00"),
                    "USD",
                    true,
                    8,
                    LocalDateTime.now().minusHours(5)
            ));
        } else {
            // Generic sample offer for any non-empty product name
            offers.add(new RawMarketplaceOffer(
                    "mock-generic-store-1",
                    "OmniStore Marketplace",
                    "https://mock-omnistore.example.com",
                    "https://mock-omnistore.example.com/catalog/sample",
                    new BigDecimal("49.99"),
                    new BigDecimal("59.99"),
                    "USD",
                    true,
                    20,
                    LocalDateTime.now().minusHours(1)
            ));
        }

        return offers;
    }
}
