package com.pricepilot.currency;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Production implementation of CurrencyConversionService.
 * Enforces deterministic, high-precision currency conversions with proper rounding.
 */
@Service
public class CurrencyConversionServiceImpl implements CurrencyConversionService {

    private final CurrencyRateProvider rateProvider;
    private final CurrencyProperties properties;

    public CurrencyConversionServiceImpl(CurrencyRateProvider rateProvider, CurrencyProperties properties) {
        this.rateProvider = Objects.requireNonNull(rateProvider, "RateProvider cannot be null");
        this.properties = Objects.requireNonNull(properties, "CurrencyProperties cannot be null");
    }

    @Override
    public BigDecimal convertToCanonical(BigDecimal amount, CurrencyCode sourceCurrency) {
        if (amount == null) {
            return null;
        }
        CurrencyCode src = sourceCurrency != null ? sourceCurrency : properties.getDefaultDisplayCurrency();
        if (src == CurrencyCode.USD) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rate = rateProvider.getExchangeRate(src);
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return amount;
        }

        // Convert from Source to USD: amount / rate
        return amount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal convertFromCanonical(BigDecimal canonicalAmount, CurrencyCode targetCurrency) {
        if (canonicalAmount == null) {
            return null;
        }
        CurrencyCode tgt = targetCurrency != null ? targetCurrency : properties.getDefaultDisplayCurrency();
        if (tgt == CurrencyCode.USD) {
            return canonicalAmount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rate = rateProvider.getExchangeRate(tgt);
        // Convert from USD to Target: canonicalAmount * rate
        BigDecimal converted = canonicalAmount.multiply(rate);
        return converted.setScale(tgt.getDecimalPrecision(), RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal convert(BigDecimal amount, CurrencyCode sourceCurrency, CurrencyCode targetCurrency) {
        if (amount == null) {
            return null;
        }
        CurrencyCode src = sourceCurrency != null ? sourceCurrency : properties.getDefaultDisplayCurrency();
        CurrencyCode tgt = targetCurrency != null ? targetCurrency : properties.getDefaultDisplayCurrency();

        if (src == tgt) {
            return amount.setScale(tgt.getDecimalPrecision(), RoundingMode.HALF_UP);
        }

        BigDecimal canonical = convertToCanonical(amount, src);
        return convertFromCanonical(canonical, tgt);
    }

    @Override
    public Money convert(Money money, CurrencyCode targetCurrency) {
        if (money == null) {
            return null;
        }
        CurrencyCode tgt = targetCurrency != null ? targetCurrency : properties.getDefaultDisplayCurrency();
        BigDecimal converted = convert(money.getAmount(), money.getCurrency(), tgt);
        return Money.of(converted, tgt);
    }

    @Override
    public CurrencyCode getCanonicalCurrency() {
        return properties.getCanonicalCurrency() != null ? properties.getCanonicalCurrency() : CurrencyCode.USD;
    }

    @Override
    public CurrencyCode getDefaultDisplayCurrency() {
        return properties.getDefaultDisplayCurrency() != null ? properties.getDefaultDisplayCurrency() : CurrencyCode.INR;
    }
}
