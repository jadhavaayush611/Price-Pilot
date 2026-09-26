package com.pricepilot.currency;

import java.math.BigDecimal;

/**
 * Service contract for deterministic monetary conversions between currencies and canonical USD.
 */
public interface CurrencyConversionService {

    /**
     * Converts an amount from the specified source currency to canonical USD.
     *
     * @param amount Monetary amount in source currency
     * @param sourceCurrency Source currency code
     * @return Converted amount in USD, or null if input amount is null
     */
    BigDecimal convertToCanonical(BigDecimal amount, CurrencyCode sourceCurrency);

    /**
     * Converts a canonical USD amount to the specified target display currency.
     *
     * @param canonicalAmount Monetary amount in canonical USD
     * @param targetCurrency Target currency code
     * @return Converted amount in target currency, or null if input amount is null
     */
    BigDecimal convertFromCanonical(BigDecimal canonicalAmount, CurrencyCode targetCurrency);

    /**
     * Converts an amount from source currency to target currency.
     *
     * @param amount Monetary amount
     * @param sourceCurrency Source currency code
     * @param targetCurrency Target currency code
     * @return Converted amount, or null if input amount is null
     */
    BigDecimal convert(BigDecimal amount, CurrencyCode sourceCurrency, CurrencyCode targetCurrency);

    /**
     * Converts a Money instance to the target currency.
     */
    Money convert(Money money, CurrencyCode targetCurrency);

    /**
     * Returns canonical internal currency (USD).
     */
    CurrencyCode getCanonicalCurrency();

    /**
     * Returns default display currency (INR).
     */
    CurrencyCode getDefaultDisplayCurrency();
}
