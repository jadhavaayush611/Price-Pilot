package com.pricepilot.currency;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic in-memory currency rate provider backed by CurrencyProperties.
 */
@Component
public class ConfiguredCurrencyRateProvider implements CurrencyRateProvider {

    private final CurrencyProperties properties;

    public ConfiguredCurrencyRateProvider(CurrencyProperties properties) {
        this.properties = Objects.requireNonNull(properties, "CurrencyProperties cannot be null");
    }

    @Override
    public BigDecimal getExchangeRate(CurrencyCode currency) {
        if (currency == null) {
            return BigDecimal.ONE;
        }
        Map<String, BigDecimal> rates = properties.getRates();
        if (rates != null && rates.containsKey(currency.name())) {
            return rates.get(currency.name());
        }
        return switch (currency) {
            case USD -> BigDecimal.ONE;
            case INR -> new BigDecimal("80.0");
            case EUR -> new BigDecimal("0.90");
            case GBP -> new BigDecimal("0.80");
            case JPY -> new BigDecimal("150.0");
        };
    }

    @Override
    public BigDecimal getRate(CurrencyCode from, CurrencyCode to) {
        if (from == null || to == null || from == to) {
            return BigDecimal.ONE;
        }
        BigDecimal fromToUsdRate = getExchangeRate(from); // 1 USD = X From => 1 From = 1/X USD
        BigDecimal usdToTargetRate = getExchangeRate(to);  // 1 USD = Y To

        if (from == CurrencyCode.USD) {
            return usdToTargetRate;
        }
        if (to == CurrencyCode.USD) {
            return BigDecimal.ONE.divide(fromToUsdRate, 8, RoundingMode.HALF_UP);
        }

        // Cross-rate: (1 / fromToUsdRate) * usdToTargetRate
        return usdToTargetRate.divide(fromToUsdRate, 8, RoundingMode.HALF_UP);
    }

    @Override
    public CurrencyCode getCanonicalCurrency() {
        return properties.getCanonicalCurrency() != null ? properties.getCanonicalCurrency() : CurrencyCode.USD;
    }
}
