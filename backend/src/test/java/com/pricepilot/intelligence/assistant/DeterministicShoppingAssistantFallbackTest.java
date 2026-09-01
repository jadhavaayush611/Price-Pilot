package com.pricepilot.intelligence.assistant;

import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.assistant.fallback.DeterministicShoppingAssistantFallback;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    @DisplayName("Generate preference query response")
    void testPreferenceQueryFallback() {
        UserShoppingPreferenceDTO prefs = UserShoppingPreferenceDTO.builder()
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1200))
                .preferredCategories(Set.of("Laptops"))
                .preferredBrands(Set.of("Dell", "Apple"))
                .dealSensitivity(com.pricepilot.intelligence.personalization.preference.DealSensitivity.HIGH)
                .availabilityPreference(com.pricepilot.intelligence.personalization.preference.AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        String response = fallback.generateFallbackResponse(AssistantIntent.PREFERENCE_QUERY, null, prefs);

        assertTrue(response.contains("Your Current Shopping Preferences"));
        assertTrue(response.contains("$500 - $1200"));
        assertTrue(response.contains("Laptops"));
        assertTrue(response.contains("Apple"));
        assertTrue(response.contains("HIGH"));
    }
}
