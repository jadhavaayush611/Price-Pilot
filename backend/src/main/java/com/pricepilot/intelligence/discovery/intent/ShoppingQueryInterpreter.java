package com.pricepilot.intelligence.discovery.intent;

/**
 * Strategy interface for interpreting natural-language shopping queries into structured intent.
 *
 * Implementations must be deterministic, repeatable, and operate without external API keys.
 */
public interface ShoppingQueryInterpreter {

    /**
     * Interprets a raw user query string into a structured ShoppingQueryIntent.
     *
     * @param rawQuery The natural-language search query entered by the user
     * @return Strongly-typed ShoppingQueryIntent separating constraints from semantic text
     */
    ShoppingQueryIntent interpret(String rawQuery);
}
