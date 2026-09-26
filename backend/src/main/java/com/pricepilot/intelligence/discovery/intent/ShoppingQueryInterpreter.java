package com.pricepilot.intelligence.discovery.intent;

/**
 * Strategy interface for interpreting natural-language shopping queries into structured intent.
 *
 * Implementations must be deterministic, repeatable, and operate without external API keys.
 */
public interface ShoppingQueryInterpreter {

    /**
     * Interprets a raw user query string into a structured ShoppingQueryIntent with default INR display currency.
     *
     * @param rawQuery The natural-language search query entered by the user
     * @return Strongly-typed ShoppingQueryIntent separating constraints from semantic text
     */
    default ShoppingQueryIntent interpret(String rawQuery) {
        return interpret(rawQuery, com.pricepilot.currency.CurrencyCode.INR);
    }

    /**
     * Interprets a raw user query string into a structured ShoppingQueryIntent with user's selected display currency.
     *
     * @param rawQuery The natural-language search query entered by the user
     * @param userCurrency The active display currency of the user (fallback for bare numbers)
     * @return Strongly-typed ShoppingQueryIntent separating constraints from semantic text
     */
    ShoppingQueryIntent interpret(String rawQuery, com.pricepilot.currency.CurrencyCode userCurrency);
}
