package com.pricepilot.intelligence.assistant.matching;

import com.pricepilot.intelligence.assistant.dto.AssistantMatchClassification;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExactProductMatchEvaluatorTest {

    private ExactProductMatchEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ExactProductMatchEvaluator(new QueryNormalizer());
    }

    private DiscoveryProductDTO createCandidate(String name, String brand, String category, double price) {
        return DiscoveryProductDTO.builder()
                .id(UUID.randomUUID())
                .name(name)
                .brand(brand)
                .category(category)
                .currentBestPrice(BigDecimal.valueOf(price))
                .inStock(true)
                .build();
    }

    @Test
    @DisplayName("Exact entity intent detection for various query formats")
    void testExactEntityIntentDetection() {
        assertTrue(evaluator.isExactProductQuery("Find the iPhone 16"));
        assertTrue(evaluator.isExactProductQuery("Show me iPhone 16"));
        assertTrue(evaluator.isExactProductQuery("I want an iPhone 16"));
        assertTrue(evaluator.isExactProductQuery("Where can I buy iPhone 16?"));
        assertTrue(evaluator.isExactProductQuery("Find Apple iPhone 16"));
        assertTrue(evaluator.isExactProductQuery("Show me the iPhone 16"));
        assertTrue(evaluator.isExactProductQuery("Find iPhone 15 Pro"));
        assertTrue(evaluator.isExactProductQuery("Show me Sony WH-1000XM5"));
        assertTrue(evaluator.isExactProductQuery("I want AirPods Pro 2"));
        assertTrue(evaluator.isExactProductQuery("Where can I buy Pixel 9?"));
        assertTrue(evaluator.isExactProductQuery("JBL Tour One M2"));
        assertTrue(evaluator.isExactProductQuery("Audio-Technica ATH-M50xBT2"));
    }

    @Test
    @DisplayName("Broad category and descriptive queries are not classified as exact-product requests")
    void testCategoryAndDescriptiveQueriesNotExactProduct() {
        assertFalse(evaluator.isExactProductQuery("Find wireless headphones"));
        assertFalse(evaluator.isExactProductQuery("Show me laptops under ₹80000"));
        assertFalse(evaluator.isExactProductQuery("I need a gaming monitor"));
        assertFalse(evaluator.isExactProductQuery("Find phones with good cameras"));
        assertFalse(evaluator.isExactProductQuery("Find me good headphones for bass-heavy music"));
        assertFalse(evaluator.isExactProductQuery("wireless headphones under ₹5000"));
        assertFalse(evaluator.isExactProductQuery("cheapest 4k monitor"));
    }

    @Test
    @DisplayName("Comparative and alternative queries are excluded from exact product matching")
    void testComparativeAndAlternativeQueriesExcluded() {
        assertFalse(evaluator.isExactProductQuery("Compare iPhone 15 and iPhone 16"));
        assertFalse(evaluator.isExactProductQuery("Which is better, Sony or Bose?"));
        assertFalse(evaluator.isExactProductQuery("Find alternatives to iPhone 15"));
        assertFalse(evaluator.isExactProductQuery("What is similar to Sony WH-1000XM5?"));
    }

    @Test
    @DisplayName("Case A — requested product absent returns CLOSE_MATCHES with predecessor/variants")
    void testCaseARequestedProductAbsent() {
        List<DiscoveryProductDTO> candidates = List.of(
                createCandidate("Apple iPhone 15", "Apple", "Smartphone", 799.0),
                createCandidate("Apple iPhone 15 Pro", "Apple", "Smartphone", 999.0)
        );

        ExactProductMatchEvaluator.EvaluationResult result = evaluator.evaluateCandidates("Find the iPhone 16", candidates, null);

        assertEquals(AssistantMatchClassification.CLOSE_MATCHES, result.getClassification());
        assertEquals("iPhone 16", result.getRequestedEntity());
        assertTrue(result.getExactMatches().isEmpty());
        assertEquals(2, result.getCloseMatches().size());
    }

    @Test
    @DisplayName("Case B — exact product exists returns EXACT_MATCH and separates exact candidate")
    void testCaseBExactProductExists() {
        List<DiscoveryProductDTO> candidates = List.of(
                createCandidate("Apple iPhone 16", "Apple", "Smartphone", 899.0),
                createCandidate("Apple iPhone 15", "Apple", "Smartphone", 799.0),
                createCandidate("Apple iPhone 15 Pro", "Apple", "Smartphone", 999.0)
        );

        ExactProductMatchEvaluator.EvaluationResult result = evaluator.evaluateCandidates("Find the iPhone 16", candidates, null);

        assertEquals(AssistantMatchClassification.EXACT_MATCH, result.getClassification());
        assertEquals("iPhone 16", result.getRequestedEntity());
        assertEquals(1, result.getExactMatches().size());
        assertEquals("Apple iPhone 16", result.getExactMatches().get(0).getName());
        assertEquals(2, result.getCloseMatches().size());
    }

    @Test
    @DisplayName("Case C — generation mismatch: iPhone 15 must never match iPhone 16 as exact")
    void testCaseCGenerationMismatch() {
        assertFalse(evaluator.isExactProductMatch("iPhone 16", "Apple iPhone 15", "Apple", "Smartphone"));
        assertFalse(evaluator.isExactProductMatch("iPhone 16", "iPhone 14", "Apple", "Smartphone"));
        assertFalse(evaluator.isExactProductMatch("Sony WH-1000XM5", "Sony WH-1000XM4", "Sony", "Headphones"));
        assertFalse(evaluator.isExactProductMatch("Pixel 9", "Google Pixel 8", "Google", "Smartphone"));
    }

    @Test
    @DisplayName("Case D — model variant mismatch: iPhone 16 must not match iPhone 16 Pro as exact")
    void testCaseDModelVariantMismatch() {
        assertFalse(evaluator.isExactProductMatch("iPhone 16", "Apple iPhone 16 Pro", "Apple", "Smartphone"));
        assertFalse(evaluator.isExactProductMatch("iPhone 16", "Apple iPhone 16 Pro Max", "Apple", "Smartphone"));
        assertFalse(evaluator.isExactProductMatch("iPhone 16 Pro", "Apple iPhone 16", "Apple", "Smartphone"));
        assertFalse(evaluator.isExactProductMatch("iPhone 16 Pro", "Apple iPhone 16 Pro Max", "Apple", "Smartphone"));
        assertTrue(evaluator.isExactProductMatch("iPhone 16 Pro", "Apple iPhone 16 Pro", "Apple", "Smartphone"));
        assertTrue(evaluator.isExactProductMatch("iPhone 16 Pro Max", "Apple iPhone 16 Pro Max", "Apple", "Smartphone"));
    }

    @Test
    @DisplayName("Case E — brand mismatch: Generic brand candidate must not match brand-specified query")
    void testCaseEBrandMismatch() {
        assertFalse(evaluator.isExactProductMatch("Sony WH-1000XM5", "Generic WH-1000XM5 Headphones", "Generic", "Headphones"));
        assertFalse(evaluator.isExactProductMatch("Apple iPhone 15", "Clone iPhone 15", "CloneBrand", "Smartphone"));
        assertTrue(evaluator.isExactProductMatch("Sony WH-1000XM5", "Sony WH-1000XM5 Wireless Noise Canceling", "Sony", "Headphones"));
    }

    @Test
    @DisplayName("Case F & G — broad category or descriptive query returns CATEGORY_RESULTS")
    void testCaseFCategoryQueryEvaluation() {
        List<DiscoveryProductDTO> candidates = List.of(
                createCandidate("Sony WH-1000XM5", "Sony", "Headphones", 399.0),
                createCandidate("Bose QuietComfort Ultra", "Bose", "Headphones", 429.0)
        );

        ExactProductMatchEvaluator.EvaluationResult result = evaluator.evaluateCandidates("Find wireless headphones", candidates, null);
        assertEquals(AssistantMatchClassification.CATEGORY_RESULTS, result.getClassification());
        assertEquals(2, result.getCloseMatches().size());
    }

    @Test
    @DisplayName("Empty candidate set returns NO_MATCH")
    void testEmptyCandidateSetReturnsNoMatch() {
        ExactProductMatchEvaluator.EvaluationResult result = evaluator.evaluateCandidates("Find electric toothbrush", List.of(), null);
        assertEquals(AssistantMatchClassification.NO_MATCH, result.getClassification());
    }

    @Test
    @DisplayName("Punctuation, case, and spacing normalization resilience")
    void testNormalizationResilience() {
        assertTrue(evaluator.isExactProductMatch("iphone-16", "Apple iPhone 16", "Apple", "Smartphone"));
        assertTrue(evaluator.isExactProductMatch("IPHONE 16!", "Apple iPhone 16", "Apple", "Smartphone"));
        assertTrue(evaluator.isExactProductMatch("audio-technica ath-m50xbt2", "Audio-Technica ATH-M50xBT2", "Audio-Technica", "Headphones"));
        assertTrue(evaluator.isExactProductMatch("jbl tour one m2", "JBL Tour One M2", "JBL", "Headphones"));
    }
}
