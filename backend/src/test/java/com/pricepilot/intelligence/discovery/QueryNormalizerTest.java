package com.pricepilot.intelligence.discovery;

import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryNormalizerTest {

    private QueryNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new QueryNormalizer();
    }

    @Test
    @DisplayName("Handles null, empty, and whitespace-only queries")
    void testEmptyAndNull() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize(""));
        assertEquals("", normalizer.normalize("   "));
        assertEquals("", normalizer.normalize("\t\n"));
    }

    @Test
    @DisplayName("Collapses multi-spaces and trims leading/trailing whitespace")
    void testWhitespaceNormalization() {
        assertEquals("iphone 15 pro", normalizer.normalize("   iPhone    15    Pro   "));
        assertEquals("macbook air m2", normalizer.normalize("macbook\t\tair\n m2"));
    }

    @Test
    @DisplayName("Removes noisy punctuation while preserving alphanumeric characters")
    void testPunctuationNormalization() {
        assertEquals("iphone 15 pro", normalizer.normalize("iPhone 15 Pro!!!???"));
        assertEquals("sony wh 1000xm5", normalizer.normalize("Sony (WH-1000XM5);;"));
    }

    @Test
    @DisplayName("Normalizes currency commas in price amounts")
    void testCurrencyCommaNormalization() {
        assertEquals("under 70000", normalizer.normalize("under $70,000"));
        assertEquals("laptop between 50000 and 80000", normalizer.normalize("Laptop between ₹50,000 and ₹80,000"));
    }

    @Test
    @DisplayName("Normalizes currency symbols to bare numbers")
    void testCurrencySymbols() {
        assertEquals("under 500", normalizer.normalize("under $500"));
        assertEquals("above 1000", normalizer.normalize("above €1000"));
        assertEquals("around 250", normalizer.normalize("around £250"));
    }
}
