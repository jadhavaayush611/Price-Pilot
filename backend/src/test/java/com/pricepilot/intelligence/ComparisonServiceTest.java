package com.pricepilot.intelligence;

import com.pricepilot.intelligence.comparison.ComparisonServiceImpl;
import com.pricepilot.intelligence.comparison.comparator.ComparisonRowRegistry;
import com.pricepilot.intelligence.comparison.comparator.StandardRowComparators;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import com.pricepilot.intelligence.comparison.entity.SavedComparisonEntity;
import com.pricepilot.intelligence.comparison.repository.ComparisonSessionRepository;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.comparison.scoring.DefaultComparisonScorer;
import com.pricepilot.intelligence.comparison.scoring.ScoringConfigProperties;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.seller.SellerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ComparisonSessionRepository comparisonSessionRepository;

    @Mock
    private SavedComparisonRepository savedComparisonRepository;

    private ScoringConfigProperties scoringProps;
    private DefaultComparisonScorer scoringStrategy;
    private ComparisonRowRegistry rowRegistry;
    private ComparisonServiceImpl comparisonService;

    @BeforeEach
    void setUp() {
        scoringProps = new ScoringConfigProperties();
        scoringStrategy = new DefaultComparisonScorer(scoringProps);

        rowRegistry = new ComparisonRowRegistry(List.of(
                new StandardRowComparators.BrandRowComparator(),
                new StandardRowComparators.CategoryRowComparator(),
                new StandardRowComparators.BestPriceRowComparator(),
                new StandardRowComparators.ListPriceRowComparator(),
                new StandardRowComparators.MaxDiscountRowComparator(),
                new StandardRowComparators.RatingRowComparator(),
                new StandardRowComparators.AvailabilityRowComparator(),
                new StandardRowComparators.SellerMerchantsRowComparator(),
                new StandardRowComparators.DisplaySpecRowComparator(),
                new StandardRowComparators.BatterySpecRowComparator(),
                new StandardRowComparators.BuildSpecRowComparator()
        ));

        comparisonService = new ComparisonServiceImpl(
                productRepository,
                comparisonSessionRepository,
                savedComparisonRepository,
                scoringStrategy,
                rowRegistry
        );
    }

    @Test
    @DisplayName("compareProducts should generate matrix for 2-product comparison via ComparisonRowRegistry")
    void testTwoProductComparison() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ProductEntity p1 = createMockProductEntity(id1, "Phone A", "BrandX", "Electronics", new BigDecimal("799.00"), new BigDecimal("899.00"));
        ProductEntity p2 = createMockProductEntity(id2, "Phone B", "BrandY", "Electronics", new BigDecimal("699.00"), new BigDecimal("799.00"));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(id1, id2))).thenReturn(List.of(p1, p2));

        ComparisonResponse response = comparisonService.compareProducts(List.of(id1, id2));

        assertNotNull(response);
        assertEquals(2, response.getProducts().size());
        assertFalse(response.getRows().isEmpty());
        assertEquals(2, response.getScores().size());

        ProductScore s2 = response.getScores().get(id2);
        assertNotNull(s2);
        assertTrue(s2.getOverallScore() > 0);
    }

    @Test
    @DisplayName("compareProducts should support up to 5-product comparison")
    void testFiveProductComparison() {
        List<UUID> ids = new ArrayList<>();
        List<ProductEntity> entities = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            entities.add(createMockProductEntity(id, "Item " + i, "Brand" + i, "Gadgets", new BigDecimal(100 * i), new BigDecimal(120 * i)));
        }

        when(productRepository.findAllByIdInWithPricesAndSellers(ids)).thenReturn(entities);

        ComparisonResponse response = comparisonService.compareProducts(ids);

        assertNotNull(response);
        assertEquals(5, response.getProducts().size());
        assertEquals(5, response.getScores().size());
    }

    @Test
    @DisplayName("compareProducts respects externalized scoring weights")
    void testExternalizedScoringWeights() {
        UUID id = UUID.randomUUID();
        ProductEntity p = createMockProductEntity(id, "Item", "Brand", "Category", new BigDecimal("100.00"), new BigDecimal("150.00"));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(id))).thenReturn(List.of(p));

        // Adjust scoring properties dynamically
        scoringProps.setPriceCompetitiveness(0.50);
        scoringProps.setDiscountPercentage(0.50);
        scoringProps.setProductRating(0.0);
        scoringProps.setPopularity(0.0);
        scoringProps.setSellerReputation(0.0);
        scoringProps.setAvailability(0.0);

        ComparisonResponse response = comparisonService.compareProducts(List.of(id));
        assertNotNull(response);
        ProductScore score = response.getScores().get(id);
        assertNotNull(score);
        assertTrue(score.getOverallScore() > 0);
    }

    @Test
    @DisplayName("getSavedComparisons supports filtering by search keyword and sorting")
    void testGetSavedComparisonsFiltering() {
        UUID userId = UUID.randomUUID();
        SavedComparisonEntity entity = new SavedComparisonEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setName("Filtered Matrix");
        entity.setProductIds(UUID.randomUUID().toString());
        entity.setCreatedAt(LocalDateTime.now());

        Page<SavedComparisonEntity> page = new PageImpl<>(List.of(entity));
        when(savedComparisonRepository.findByUserIdAndSearch(eq(userId), eq("Filtered"), any(Pageable.class))).thenReturn(page);

        Page<SavedComparisonResponseDTO> result = comparisonService.getSavedComparisons(userId, 0, 10, "name", "asc", "Filtered");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Filtered Matrix", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("compareProducts handles missing specifications and prices gracefully")
    void testMissingSpecificationsAndPrices() {
        UUID id1 = UUID.randomUUID();
        ProductEntity p1 = ProductEntity.builder()
                .name("No Price Item")
                .brand(null)
                .category("Misc")
                .description(null)
                .productPrices(Collections.emptyList())
                .build();
        p1.setId(id1);

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(id1))).thenReturn(List.of(p1));

        ComparisonResponse response = comparisonService.compareProducts(List.of(id1));

        assertNotNull(response);
        assertEquals(1, response.getProducts().size());
        ProductScore score = response.getScores().get(id1);
        assertNotNull(score);
        assertEquals(50.0, score.getBreakdown().get("PriceCompetitiveness"));
    }

    @Test
    @DisplayName("compareProducts handles identical products and tie scoring correctly")
    void testIdenticalProductsTieHandling() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ProductEntity p1 = createMockProductEntity(id1, "Identical Phone", "SameBrand", "Electronics", new BigDecimal("500.00"), new BigDecimal("600.00"));
        ProductEntity p2 = createMockProductEntity(id2, "Identical Phone", "SameBrand", "Electronics", new BigDecimal("500.00"), new BigDecimal("600.00"));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(id1, id2))).thenReturn(List.of(p1, p2));

        ComparisonResponse response = comparisonService.compareProducts(List.of(id1, id2));

        assertNotNull(response);
        ProductScore s1 = response.getScores().get(id1);
        ProductScore s2 = response.getScores().get(id2);

        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1.getBreakdown().get("PriceCompetitiveness"), s2.getBreakdown().get("PriceCompetitiveness"));
    }

    @Test
    @DisplayName("compareProducts with empty comparison should return empty result")
    void testEmptyComparison() {
        ComparisonResponse response = comparisonService.compareProducts(List.of());
        assertNotNull(response);
        assertTrue(response.getProducts().isEmpty());
        assertTrue(response.getRows().isEmpty());
        assertTrue(response.getScores().isEmpty());
        assertEquals("No products selected for comparison.", response.getSummary());
    }

    @Test
    @DisplayName("compareProducts handles deleted or invalid product IDs gracefully")
    void testDeletedProduct() {
        UUID validId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();

        ProductEntity p1 = createMockProductEntity(validId, "Active Phone", "BrandX", "Electronics", new BigDecimal("300.00"), new BigDecimal("400.00"));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(validId, deletedId))).thenReturn(List.of(p1));

        ComparisonResponse response = comparisonService.compareProducts(List.of(validId, deletedId));

        assertNotNull(response);
        assertEquals(1, response.getProducts().size());
        assertEquals(validId, response.getProducts().get(0).getId());
    }

    @Test
    @DisplayName("getComparisonSession throws AccessDeniedException when unauthorized user attempts access")
    void testUnauthorizedSavedComparisonAccess() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        SavedComparisonEntity saved = new SavedComparisonEntity();
        saved.setId(sessionId);
        saved.setUserId(ownerId);
        saved.setName("Private Comparison");
        saved.setProductIds(UUID.randomUUID().toString());

        when(savedComparisonRepository.findById(sessionId)).thenReturn(Optional.of(saved));

        assertThrows(AccessDeniedException.class, () -> comparisonService.getComparisonSession(sessionId, strangerId));
    }

    @Test
    @DisplayName("deleteSavedComparison throws AccessDeniedException when unauthorized user attempts deletion")
    void testUnauthorizedDeleteSavedComparison() {
        UUID comparisonId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        SavedComparisonEntity saved = new SavedComparisonEntity();
        saved.setId(comparisonId);
        saved.setUserId(ownerId);

        when(savedComparisonRepository.findById(comparisonId)).thenReturn(Optional.of(saved));

        assertThrows(AccessDeniedException.class, () -> comparisonService.deleteSavedComparison(strangerId, comparisonId));
    }

    private ProductEntity createMockProductEntity(UUID id, String name, String brand, String category, BigDecimal currentPrice, BigDecimal originalPrice) {
        ProductEntity product = ProductEntity.builder()
                .name(name)
                .brand(brand)
                .category(category)
                .description("Sample description for " + name)
                .archived(false)
                .build();
        product.setId(id);

        SellerEntity seller = SellerEntity.builder()
                .name("Test Seller")
                .websiteUrl("https://seller.com")
                .build();
        seller.setId(UUID.randomUUID());

        ProductPriceEntity price = ProductPriceEntity.builder()
                .currentPrice(currentPrice)
                .originalPrice(originalPrice)
                .discountPercentage(BigDecimal.TEN)
                .lastUpdated(LocalDateTime.now())
                .product(product)
                .seller(seller)
                .build();
        price.setId(UUID.randomUUID());

        product.setProductPrices(List.of(price));
        return product;
    }
}
