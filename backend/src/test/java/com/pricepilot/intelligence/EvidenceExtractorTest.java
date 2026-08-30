package com.pricepilot.intelligence;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.evidence.EvidenceExtractor;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceExtractorTest {

    private EvidenceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EvidenceExtractor();
    }

    private ProductResponseDTO createProduct(UUID id, String name, BigDecimal price, BigDecimal discount, int sellerCount) {
        ProductResponseDTO p = new ProductResponseDTO();
        p.setId(id);
        p.setName(name);
        List<ProductPriceResponseDTO> prices = new ArrayList<>();
        for (int i = 0; i < sellerCount; i++) {
            ProductPriceResponseDTO pr = new ProductPriceResponseDTO();
            pr.setCurrentPrice(price);
            pr.setDiscountPercentage(discount);
            prices.add(pr);
        }
        p.setPrices(prices);
        return p;
    }

    @Test
    @DisplayName("Extracts LOWEST_PRICE when recommended product has lowest price")
    void testLowestPriceExtraction() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProductResponseDTO p1 = createProduct(id1, "Cheaper", BigDecimal.valueOf(100), BigDecimal.ZERO, 2);
        ProductResponseDTO p2 = createProduct(id2, "Expensive", BigDecimal.valueOf(200), BigDecimal.ZERO, 2);

        Map<UUID, ProductScore> scores = Map.of(
                id1, new ProductScore(id1, "Cheaper", 90.0, 95.0, 85.0, 90.0, Map.of(), "TOP PICK"),
                id2, new ProductScore(id2, "Expensive", 80.0, 70.0, 85.0, 85.0, Map.of(), "VAL")
        );

        EvidenceExtractor.ExtractedEvidence result = extractor.extractEvidence(p1, List.of(p1, p2), scores);

        assertTrue(result.positiveEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.LOWEST_PRICE));
        EvidenceItem item = result.positiveEvidence().stream().filter(e -> e.getType() == EvidenceType.LOWEST_PRICE).findFirst().orElseThrow();
        assertEquals(100.0, item.getMetricValue());
        assertTrue(item.isPositive());
    }

    @Test
    @DisplayName("Extracts HIGHEST_DISCOUNT when recommended product has steepest discount")
    void testHighestDiscountExtraction() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProductResponseDTO p1 = createProduct(id1, "BigDiscount", BigDecimal.valueOf(100), BigDecimal.valueOf(30), 2);
        ProductResponseDTO p2 = createProduct(id2, "SmallDiscount", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2);

        Map<UUID, ProductScore> scores = Map.of(
                id1, new ProductScore(id1, "BigDiscount", 90.0, 90.0, 85.0, 90.0, Map.of(), "TOP PICK"),
                id2, new ProductScore(id2, "SmallDiscount", 80.0, 80.0, 85.0, 85.0, Map.of(), "VAL")
        );

        EvidenceExtractor.ExtractedEvidence result = extractor.extractEvidence(p1, List.of(p1, p2), scores);

        assertTrue(result.positiveEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.HIGHEST_DISCOUNT));
        EvidenceItem item = result.positiveEvidence().stream().filter(e -> e.getType() == EvidenceType.HIGHEST_DISCOUNT).findFirst().orElseThrow();
        assertEquals(30.0, item.getMetricValue());
    }

    @Test
    @DisplayName("Extracts trade-offs when competitor is cheaper or has higher rating")
    void testTradeOffExtraction() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        // p1 is recommended overall, but costs more than p2
        ProductResponseDTO p1 = createProduct(id1, "Premium", BigDecimal.valueOf(500), BigDecimal.ZERO, 3);
        ProductResponseDTO p2 = createProduct(id2, "Budget", BigDecimal.valueOf(300), BigDecimal.ZERO, 1);

        Map<UUID, ProductScore> scores = Map.of(
                id1, new ProductScore(id1, "Premium", 92.0, 85.0, 95.0, 95.0, Map.of(), "TOP PICK"),
                id2, new ProductScore(id2, "Budget", 82.0, 92.0, 75.0, 80.0, Map.of(), "VAL")
        );

        EvidenceExtractor.ExtractedEvidence result = extractor.extractEvidence(p1, List.of(p1, p2), scores);

        assertTrue(result.negativeTradeOffs().stream().anyMatch(e -> e.getType() == EvidenceType.HIGHER_PRICE),
                "Should extract HIGHER_PRICE trade-off when recommended product costs more than an alternative");
    }

    @Test
    @DisplayName("Extracts HIGH_SELLER_AVAILABILITY when 3 or more merchants offer product")
    void testSellerAvailability() {
        UUID id1 = UUID.randomUUID();
        ProductResponseDTO p1 = createProduct(id1, "MultiSeller", BigDecimal.valueOf(100), BigDecimal.ZERO, 4);

        Map<UUID, ProductScore> scores = Map.of(
                id1, new ProductScore(id1, "MultiSeller", 90.0, 90.0, 90.0, 90.0, Map.of(), "TOP PICK")
        );

        EvidenceExtractor.ExtractedEvidence result = extractor.extractEvidence(p1, List.of(p1), scores);

        assertTrue(result.positiveEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.HIGH_SELLER_AVAILABILITY));
    }
}
