package com.pricepilot.currency;

import java.math.BigDecimal;

/**
 * Provider interface for currency exchange rates against canonical USD.
 */
public interface CurrencyRateProvider {

    /**
     * Gets exchange rate from canonical USD to given currency.
     * 1 USD = rate * targetCurrency
     *
     * @param currency Target currency code
     * @return Multiplier rate
     */
    BigDecimal getExchangeRate(CurrencyCode currency);

    /**
     * Gets rate from source currency to target currency.
     */
    BigDecimal getRate(CurrencyCode from, CurrencyCode to);

    /**
     * Returns the canonical currency (always USD).
     */
    CurrencyCode getCanonicalCurrency();
}
