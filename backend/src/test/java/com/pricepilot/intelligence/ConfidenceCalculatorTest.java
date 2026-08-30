package com.pricepilot.intelligence;

import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConfidenceCalculatorTest {

    private ConfidenceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConfidenceCalculator();
    }

    private ProductResponseDTO createProduct(UUID id, String name, BigDecimal price, boolean withSeller) {
        ProductResponseDTO p = new ProductResponseDTO();
        p.setId(id);
        p.setName(name);
        p.setDescription("Detailed description for " + name);
        if (price != null) {
            ProductPriceResponseDTO pr = new ProductPriceResponseDTO();
            pr.setCurrentPrice(price);
            if (withSeller) {
                SellerResponseDTO s = new SellerResponseDTO();
                s.setName("BestSeller");
                pr.setSeller(s);
            }
            p.setPrices(List.of(pr));
        } else {
            p.setPrices(List.of());
        }
        return p;
    }

    private ProductScore createScore(UUID id, double overall) {
        return new ProductScore(id, "P", overall, overall, overall, overall, Map.of(), "TOP PICK");
    }

    @Test
    @DisplayName("High score separation produces strong confidence")
    void testHighScoreSeparation() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        List<ProductResponseDTO> candidates = List.of(
                createProduct(id1, "Prod1", BigDecimal.valueOf(100), true),
                createProduct(id2, "Prod2", BigDecimal.valueOf(200), true)
        );

        List<ProductScore> scores = List.of(
                createScore(id1, 95.0),
                createScore(id2, 75.0) // gap = 20 (>= 15 pts -> 0.40)
        );

        List<EvidenceItem> positive = List.of(
                new EvidenceItem(id1, "Prod1", EvidenceType.LOWEST_PRICE, "Lowest price", "PRICE", 100, 200, true, 0.9),
                new EvidenceItem(id1, "Prod1", EvidenceType.HIGHEST_RATING, "Highest rating", "RATING", 4.9, 4.5, true, 0.9),
                new EvidenceItem(id1, "Prod1", EvidenceType.HIGH_SELLER_AVAILABILITY, "Sellers", "SELLER", 3, 1, true, 0.7)
        );

        double conf = calculator.calculateConfidence(candidates, scores, positive, List.of());

        assertTrue(conf >= 0.85, "Confidence should be >= 0.85 for strong separation and multiple supporting factors, was: " + conf);
        assertTrue(conf <= 0.99, "Confidence must be <= 0.99");
    }

    @Test
    @DisplayName("Score tie produces lower confidence")
    void testScoreTieSeparation() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        List<ProductResponseDTO> candidates = List.of(
                createProduct(id1, "Prod1", BigDecimal.valueOf(100), true),
                createProduct(id2, "Prod2", BigDecimal.valueOf(100), true)
        );

        List<ProductScore> scores = List.of(
                createScore(id1, 88.0),
                createScore(id2, 88.0) // gap = 0 -> 0.10
        );

        double confTie = calculator.calculateConfidence(candidates, scores, List.of(), List.of());

        // Same candidates with large score gap
        List<ProductScore> scoresGap = List.of(
                createScore(id1, 95.0),
                createScore(id2, 75.0)
        );
        double confGap = calculator.calculateConfidence(candidates, scoresGap, List.of(), List.of());

        assertTrue(confTie < confGap, "Tie confidence must be strictly lower than clear leader confidence");
    }

    @Test
    @DisplayName("Missing data penalizes confidence")
    void testMissingDataPenalty() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        // Complete data
        List<ProductResponseDTO> complete = List.of(
                createProduct(id1, "Prod1", BigDecimal.valueOf(100), true),
                createProduct(id2, "Prod2", BigDecimal.valueOf(150), true)
        );

        // Incomplete data: missing prices and sellers
        List<ProductResponseDTO> incomplete = List.of(
                createProduct(id1, "Prod1", null, false),
                createProduct(id2, "Prod2", null, false)
        );

        List<ProductScore> scores = List.of(createScore(id1, 90.0), createScore(id2, 85.0));

        double confComplete = calculator.calculateConfidence(complete, scores, List.of(), List.of());
        double confIncomplete = calculator.calculateConfidence(incomplete, scores, List.of(), List.of());

        assertTrue(confIncomplete < confComplete, "Incomplete data should penalize confidence score");
    }

    @Test
    @DisplayName("Conflicting signals reduce confidence")
    void testConflictingSignalsPenalty() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        List<ProductResponseDTO> candidates = List.of(
                createProduct(id1, "Prod1", BigDecimal.valueOf(100), true),
                createProduct(id2, "Prod2", BigDecimal.valueOf(150), true)
        );
        List<ProductScore> scores = List.of(createScore(id1, 90.0), createScore(id2, 85.0));
        List<EvidenceItem> positive = List.of(
                new EvidenceItem(id1, "Prod1", EvidenceType.LOWEST_PRICE, "Lowest price", "PRICE", 100, 150, true, 0.9)
        );

        // No conflicting trade-offs
        double confConsistent = calculator.calculateConfidence(candidates, scores, positive, List.of());

        // Conflicting trade-offs
        List<EvidenceItem> tradeOffs = List.of(
                new EvidenceItem(id2, "Prod2", EvidenceType.BETTER_SPECIFICATION, "Better display", "DISPLAY", null, null, false, 0.8),
                new EvidenceItem(id2, "Prod2", EvidenceType.HIGHEST_RATING, "Higher rating", "RATING", 4.9, 4.2, false, 0.8)
        );
        double confConflicting = calculator.calculateConfidence(candidates, scores, positive, tradeOffs);

        assertTrue(confConflicting < confConsistent, "Conflicting trade-offs should reduce confidence");
    }

    @Test
    @DisplayName("Confidence remains clamped between 0.10 and 0.99")
    void testConfidenceClamping() {
        double confEmpty = calculator.calculateConfidence(List.of(), List.of(), List.of(), List.of());
        assertTrue(confEmpty >= 0.10 && confEmpty <= 0.99);

        // Heavily penalized
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<ProductResponseDTO> incomplete = List.of(
                createProduct(id1, "P1", null, false),
                createProduct(id2, "P2", null, false)
        );
        List<ProductScore> tieScores = List.of(createScore(id1, 50.0), createScore(id2, 50.0));
        List<EvidenceItem> heavyTradeOffs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            heavyTradeOffs.add(new EvidenceItem(id2, "P2", EvidenceType.BETTER_SPECIFICATION, "Spec", "S", 1, 2, false, 1.0));
        }
        double heavilyPenalized = calculator.calculateConfidence(incomplete, tieScores, List.of(), heavyTradeOffs);
        assertTrue(heavilyPenalized >= 0.10, "Should clamp at lower bound 0.10");
    }
}
