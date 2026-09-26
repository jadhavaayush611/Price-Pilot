package com.pricepilot.currency;

import lombok.Getter;

import java.util.Locale;

/**
 * Enumeration of supported currencies across PricePilot.
 *
 * USD is the internal canonical currency for storage, comparisons, scoring, and analytics.
 * INR is the default user-facing display currency.
 */
@Getter
public enum CurrencyCode {

    INR("₹", "Indian Rupee", "en-IN", 2),
    USD("$", "US Dollar", "en-US", 2),
    EUR("€", "Euro", "de-DE", 2),
    GBP("£", "British Pound", "en-GB", 2),
    JPY("¥", "Japanese Yen", "ja-JP", 0);

    private final String symbol;
    private final String displayName;
    private final String localeTag;
    private final int decimalPrecision;

    CurrencyCode(String symbol, String displayName, String localeTag, int decimalPrecision) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.localeTag = localeTag;
        this.decimalPrecision = decimalPrecision;
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(localeTag);
    }

    /**
     * Safe lookup of CurrencyCode by code name, with fallback.
     *
     * @param code Currency code string (e.g., "INR", "USD", "EUR")
     * @param defaultCode Fallback code if null or unrecognized
     * @return Resolved CurrencyCode
     */
    public static CurrencyCode fromCode(String code, CurrencyCode defaultCode) {
        if (code == null || code.trim().isEmpty()) {
            return defaultCode;
        }
        String clean = code.trim().toUpperCase(Locale.ROOT);
        try {
            return CurrencyCode.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return defaultCode;
        }
    }

    /**
     * Safe lookup of CurrencyCode defaulting to INR.
     */
    public static CurrencyCode fromCode(String code) {
        return fromCode(code, INR);
    }

    /**
     * Matches currency code from symbol or text prefix/suffix.
     *
     * @param token Currency symbol or token (e.g., "₹", "rs", "inr", "$", "usd", "€", "eur", "£", "gbp", "¥", "jpy")
     * @return Optional CurrencyCode
     */
    public static CurrencyCode fromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String clean = token.trim().toLowerCase(Locale.ROOT).replaceAll("\\.", "");
        return switch (clean) {
            case "₹", "rs", "inr", "rupee", "rupees" -> INR;
            case "$", "usd", "dollar", "dollars" -> USD;
            case "€", "eur", "euro", "euros" -> EUR;
            case "£", "gbp", "pound", "pounds" -> GBP;
            case "¥", "jpy", "yen" -> JPY;
            default -> null;
        };
    }
}
