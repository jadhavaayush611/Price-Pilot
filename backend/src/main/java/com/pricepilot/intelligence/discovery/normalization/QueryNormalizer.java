package com.pricepilot.intelligence.discovery.normalization;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Deterministic query normalizer for search and discovery.
 * Normalizes casing, collapses whitespace, standardizes punctuation, and removes harmless noisy characters.
 */
@Component
public class QueryNormalizer {

    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOISY_PUNCTUATION = Pattern.compile("[!?,;:\\\"'()\\[\\]{}<>~`]");
    private static final Pattern CURRENCY_COMMAS = Pattern.compile("(?<=\\d),(?=\\d)");

    /**
     * Normalizes a raw query string.
     *
     * @param rawQuery user input query
     * @return normalized query string, or empty string if input is null or whitespace-only
     */
    public String normalize(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }

        String query = rawQuery.trim().toLowerCase();
        if (query.isEmpty()) {
            return "";
        }

        // 1. Remove commas in numbers like 70,000 -> 70000
        query = CURRENCY_COMMAS.matcher(query).replaceAll("");

        // 2. Standardize common currency symbols and punctuation
        query = query.replace("$", "").replace("₹", "").replace("€", "").replace("£", "");

        // 3. Remove harmless noisy punctuation
        query = NOISY_PUNCTUATION.matcher(query).replaceAll(" ");

        // 4. Collapse hyphens between words into spaces unless part of specific model names
        query = query.replace("-", " ");

        // 5. Collapse multiple whitespace characters into a single space
        query = MULTI_WHITESPACE.matcher(query).replaceAll(" ").trim();

        return query;
    }
}
