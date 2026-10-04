package com.pricepilot.intelligence.assistant;

import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.assistant.fallback.DeterministicShoppingAssistantFallback;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicShoppingAssistantFallbackTest {

    private DeterministicShoppingAssistantFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = new DeterministicShoppingAssistantFallback();
    }

    @Test
    @DisplayName("Generate comparison fallback response with evidence and trade-offs")
    void testComparisonFallback() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(AssistantIntent.COMPARISON)
                .factualEvidence(List.of(
                        GroundedEvidenceItem.builder()
                                .productId(p1)
                                .productName("iPhone 16")
                                .description("Comparison Score: 92.5/100 (Badge: TOP_PICK)")
                                .build(),
                        GroundedEvidenceItem.builder()
                                .productId(p2)
                                .productName("Samsung S24")
                                .description("Comparison Score: 88.0/100 (Badge: GREAT_DEAL)")
                                .build()
                ))
                .personalizationReasoning(List.of(
                        PersonalizationReasoningItem.builder()
                                .explanation("iPhone 16 matches your preferred brand Apple")
                                .build()
                ))
                .tradeOffs(List.of(
                        TradeOffItem.builder()
                                .description("Samsung S24 is $150 cheaper, but iPhone 16 scores higher on longevity")
                                .build()
                ))
                .unknownOrInsufficientData(List.of("No battery test metrics recorded"))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.COMPARISON, bundle, null);

        assertNotNull(response);
        assertTrue(response.contains("iPhone 16"));
        assertTrue(response.contains("Samsung S24"));
        assertTrue(response.contains("Personalization Notes:"));
        assertTrue(response.contains("Key Trade-Offs & Considerations:"));
        assertTrue(response.contains("Data Notes:"));
        assertTrue(response.contains("No battery test metrics recorded"));
    }

    @Test
    @DisplayName("Generate price analysis fallback response")
    void testPriceAnalysisFallback() {
        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(AssistantIntent.PRICE_ANALYSIS)
                .factualEvidence(List.of(
                        GroundedEvidenceItem.builder()
                                .productName("Sony WH-1000XM5")
                                .description("Current: $349.99 | Historical Low: $299.99 | Historical High: $399.99")
                                .build(),
                        GroundedEvidenceItem.builder()
                                .productName("Sony WH-1000XM5")
                                .description("Deal Rating: GOOD_DEAL (Signal: BUY_NOW, Trend: STABLE)")
                                .build()
                ))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.PRICE_ANALYSIS, bundle, null);

        assertTrue(response.contains("Price Intelligence & Purchase Timing Analysis"));
        assertTrue(response.contains("GOOD_DEAL"));
        assertTrue(response.contains("Historical Low: $299.99"));
    }

    @Test
    @DisplayName("Generate preference query response for USD user")
    void testPreferenceQueryFallbackUsd() {
        UserShoppingPreferenceDTO prefs = UserShoppingPreferenceDTO.builder()
                .currency(com.pricepilot.currency.CurrencyCode.USD)
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1200))
                .preferredCategories(Set.of("Laptops"))
                .preferredBrands(Set.of("Dell", "Apple"))
                .dealSensitivity(com.pricepilot.intelligence.personalization.preference.DealSensitivity.HIGH)
                .availabilityPreference(com.pricepilot.intelligence.personalization.preference.AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.PREFERENCE_QUERY, null, prefs);

        assertTrue(response.contains("Your Current Shopping Preferences"));
        assertTrue(response.contains("$500.00 - $1200.00"));
        assertTrue(response.contains("Laptops"));
        assertTrue(response.contains("Apple"));
        assertTrue(response.contains("HIGH"));
    }

    @Test
    @DisplayName("Generate preference query response for INR user")
    void testPreferenceQueryFallbackInr() {
        UserShoppingPreferenceDTO prefs = UserShoppingPreferenceDTO.builder()
                .currency(com.pricepilot.currency.CurrencyCode.INR)
                .minBudget(BigDecimal.valueOf(40000))
                .maxBudget(BigDecimal.valueOf(96000))
                .preferredCategories(Set.of("Laptops"))
                .preferredBrands(Set.of("Dell", "Apple"))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.PREFERENCE_QUERY, null, prefs);

        assertTrue(response.contains("Your Current Shopping Preferences"));
        assertTrue(response.contains("₹40000.00 - ₹96000.00"));
    }

    @Test
    @DisplayName("Generate preference query response for JPY user with 0 decimal places")
    void testPreferenceQueryFallbackJpy() {
        UserShoppingPreferenceDTO prefs = UserShoppingPreferenceDTO.builder()
                .currency(com.pricepilot.currency.CurrencyCode.JPY)
                .minBudget(BigDecimal.valueOf(75000))
                .maxBudget(BigDecimal.valueOf(180000))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.PREFERENCE_QUERY, null, prefs);

        assertTrue(response.contains("Your Current Shopping Preferences"));
        assertTrue(response.contains("¥75000 - ¥180000"));
        assertFalse(response.contains("¥75000.00"), "JPY must not have decimal places");
    }

    @Test
    @DisplayName("Generate discovery response with custom currency formatted items")
    void testDiscoveryResponseCurrencyFormatting() {
        for (com.pricepilot.currency.CurrencyCode cur : com.pricepilot.currency.CurrencyCode.values()) {
            UserShoppingPreferenceDTO prefs = UserShoppingPreferenceDTO.builder()
                    .currency(cur)
                    .build();

            Map<String, Object> product = new HashMap<>();
            product.put("productName", "Test Laptop");
            product.put("price", 1000.0);
            product.put("currency", cur.name());
            product.put("currencySymbol", cur.getSymbol());

            AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                    .intent(AssistantIntent.DISCOVERY)
                    .groundedProducts(List.of(product))
                    .build();

            String response = fallback.generateFallbackResponse(AssistantIntent.DISCOVERY, bundle, prefs);

            assertTrue(response.contains("Test Laptop"));
            assertTrue(response.contains(cur.getSymbol()), "Must contain symbol " + cur.getSymbol());
            if (cur == com.pricepilot.currency.CurrencyCode.JPY) {
                assertTrue(response.contains("¥1000"));
                assertFalse(response.contains("¥1000.00"));
            } else {
                assertTrue(response.contains(cur.getSymbol() + "1000.00"));
            }
        }
    }

    @Test
    @DisplayName("Exact match fallback response states 'Exact match found in the verified catalog:'")
    void testExactMatchFallbackResponse() {
        Map<String, Object> product = new HashMap<>();
        product.put("productName", "iPhone 15");
        product.put("brand", "Apple");
        product.put("price", 799.0);
        product.put("currency", "USD");
        product.put("currencySymbol", "$");

        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(AssistantIntent.DISCOVERY)
                .matchClassification(AssistantMatchClassification.EXACT_MATCH)
                .requestedEntity("iPhone 15")
                .groundedProducts(List.of(product))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.DISCOVERY, bundle, null);

        assertTrue(response.contains("Exact match found in the verified catalog:"));
        assertTrue(response.contains("iPhone 15"));
        assertFalse(response.contains("matching products"));
    }

    @Test
    @DisplayName("Close match fallback response states 'No exact iPhone 16 was found' and 'Closest available matches:'")
    void testCloseMatchFallbackResponse() {
        Map<String, Object> p1 = new HashMap<>();
        p1.put("productName", "iPhone 15");
        p1.put("brand", "Apple");
        p1.put("price", 63556.0);
        p1.put("currency", "INR");
        p1.put("currencySymbol", "₹");

        Map<String, Object> p2 = new HashMap<>();
        p2.put("productName", "iPhone 15 Pro");
        p2.put("brand", "Apple");
        p2.put("price", 124900.0);
        p2.put("currency", "INR");
        p2.put("currencySymbol", "₹");

        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(AssistantIntent.DISCOVERY)
                .matchClassification(AssistantMatchClassification.CLOSE_MATCHES)
                .requestedEntity("iPhone 16")
                .groundedProducts(List.of(p1, p2))
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.DISCOVERY, bundle, null);

        assertTrue(response.contains("No exact iPhone 16 was found in the verified catalog."));
        assertTrue(response.contains("Closest available matches:"));
        assertTrue(response.contains("iPhone 15"));
        assertTrue(response.contains("iPhone 15 Pro"));
        assertFalse(response.contains("I found the following matching products"));
    }

    @Test
    @DisplayName("No match fallback response states 'I couldn't find an exact match or sufficiently close product'")
    void testNoMatchFallbackResponse() {
        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(AssistantIntent.DISCOVERY)
                .matchClassification(AssistantMatchClassification.NO_MATCH)
                .requestedEntity("Electric Toothbrush")
                .groundedProducts(Collections.emptyList())
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.DISCOVERY, bundle, null);

        assertTrue(response.contains("I couldn't find an exact match or sufficiently close product in the verified catalog."));
        assertFalse(response.contains("matching products"));
    }
}
