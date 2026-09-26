package com.pricepilot.currency;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Externalized configuration for PricePilot currency system.
 */
@Configuration
@ConfigurationProperties(prefix = "pricepilot.currency")
@Getter
@Setter
public class CurrencyProperties {

    /**
     * Internal canonical currency for database storage and core algorithms.
     */
    private CurrencyCode canonicalCurrency = CurrencyCode.USD;

    /**
     * Default user-facing display currency.
     */
    private CurrencyCode defaultDisplayCurrency = CurrencyCode.INR;

    /**
     * Fixed deterministic exchange rates relative to 1.0 unit of canonical USD.
     * 1 USD = rate * Currency
     */
    private Map<String, BigDecimal> rates = new LinkedHashMap<>();

    public CurrencyProperties() {
        // Default deterministic rates
        rates.put("USD", new BigDecimal("1.0"));
        rates.put("INR", new BigDecimal("80.0"));
        rates.put("EUR", new BigDecimal("0.90"));
        rates.put("GBP", new BigDecimal("0.80"));
        rates.put("JPY", new BigDecimal("150.0"));
    }
}
