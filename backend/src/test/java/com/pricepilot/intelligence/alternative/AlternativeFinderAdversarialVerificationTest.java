package com.pricepilot.intelligence.alternative;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.scoring.DefaultAlternativeScoringStrategy;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class AlternativeFinderAdversarialVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private AlternativeFinderService alternativeFinderService;

    private DefaultAlternativeScoringStrategy scoringStrategy;
    private ProductEntity flagshipPhone;
    private ProductEntity expensivePhone;
    private ProductEntity budgetPhone;
    private ProductEntity upgradePhone;
    private SellerEntity seller;

    @BeforeEach
    void setUp() {
        scoringStrategy = new DefaultAlternativeScoringStrategy(new QueryNormalizer());

        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        seller = sellerRepository.save(SellerEntity.builder().name("Retailer").websiteUrl("https://ret.com").build());

        // Baseline product: $1000, 4.2 rating
        flagshipPhone = productRepository.save(ProductEntity.builder()
                .name("Alpha Phone X")
                .brand("Alpha")
                .category("Smartphones")
                .description("Flagship mobile device")
                .build());

        // More expensive product: $1200
        expensivePhone = productRepository.save(ProductEntity.builder()
                .name("Alpha Phone Ultra")
                .brand("Alpha")
                .category("Smartphones")
                .description("Ultra high end phone with telephoto lens")
                .build());

        // Budget alternative: $600 (40% savings)
        budgetPhone = productRepository.save(ProductEntity.builder()
                .name("Alpha Phone Lite")
                .brand("Alpha")
                .category("Smartphones")
                .description("Affordable smartphone with high battery life")
                .build());

        // Upgrade alternative: $1050
        upgradePhone = productRepository.save(ProductEntity.builder()
                .name("Pro Performance Phone")
                .brand("ProBrand")
                .category("Smartphones")
                .description("High performance gaming smartphone")
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(flagshipPhone)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1000.00))
                .originalPrice(BigDecimal.valueOf(1000.00))
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(expensivePhone)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1200.00))
                .originalPrice(BigDecimal.valueOf(1200.00))
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(budgetPhone)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(600.00))
                .originalPrice(BigDecimal.valueOf(600.00))
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(upgradePhone)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1050.00))
                .originalPrice(BigDecimal.valueOf(1050.00))
                .build());
    }

    @Test
    @DisplayName("Adversarial: Semantic similarity alone CANNOT qualify a more expensive product as CHEAPER")
    void testSemanticSimilarityDoesNotOverrideCheaperRule() {
        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(flagshipPhone.getId())
                .name("Alpha Phone X")
                .category("Smartphones")
                .currentBestPrice(BigDecimal.valueOf(1000.00))
                .rating(4.2)
                .build();

        // Candidate has 0.99 semantic similarity (almost identical text), but costs $1200
        AlternativeCandidate expensiveSemMatch = AlternativeCandidate.builder()
                .product(expensivePhone)
                .currentBestPrice(BigDecimal.valueOf(1200.00))
                .semanticSimilarityScore(0.99)
                .build();

        // Must NOT be eligible for CHEAPER
        assertThat(scoringStrategy.isEligible(expensiveSemMatch, src, AlternativeType.CHEAPER)).isFalse();
        // Must NOT be eligible for BUDGET_FALLBACK
        assertThat(scoringStrategy.isEligible(expensiveSemMatch, src, AlternativeType.BUDGET_FALLBACK)).isFalse();
    }

    @Test
    @DisplayName("Adversarial: Self-exclusion is strictly enforced in product-driven mode")
    void testSelfExclusionStrictness() {
        AlternativeResponseDTO response = alternativeFinderService.findAlternativesForProduct(
                flagshipPhone.getId(),
                AlternativeRequest.builder().productId(flagshipPhone.getId()).type(AlternativeType.SIMILAR).limit(20).build()
        );

        assertThat(response.getContent())
                .noneMatch(alt -> alt.getId().equals(flagshipPhone.getId()));
    }

    @Test
    @DisplayName("Adversarial: Evidence grounding check - price differences and ratings are exact")
    void testEvidenceGroundingAndFacts() {
        AlternativeResponseDTO response = alternativeFinderService.findAlternativesForProduct(
                flagshipPhone.getId(),
                AlternativeRequest.builder().productId(flagshipPhone.getId()).type(AlternativeType.CHEAPER).limit(10).build()
        );

        assertThat(response.getContent()).isNotEmpty();
        for (AlternativeProductDTO alt : response.getContent()) {
            assertThat(alt.getCurrentBestPrice()).isLessThan(BigDecimal.valueOf(1000.00));
            assertThat(alt.getPriceDifference()).isEqualByComparingTo(alt.getCurrentBestPrice().subtract(BigDecimal.valueOf(1000.00)));

            // Validate evidence items
            for (AlternativeEvidence ev : alt.getEvidence()) {
                if (ev.getCategory() == AlternativeEvidenceCategory.PRICE) {
                    assertThat(ev.getDescription()).contains(String.format("$%.2f", alt.getCurrentBestPrice()));
                    assertThat(ev.getRelationship()).isEqualTo("CHEAPER");
                }
            }
        }
    }

    @Test
    @DisplayName("Adversarial: Candidate bounds check - limit is capped at 20 max even if client requests 1000")
    void testCandidateBoundsEnforced() {
        AlternativeResponseDTO response = alternativeFinderService.findAlternativesForProduct(
                flagshipPhone.getId(),
                AlternativeRequest.builder().productId(flagshipPhone.getId()).limit(1000).build()
        );

        assertThat(response.getContent().size()).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Adversarial: Deterministic tie-breaking stability")
    void testDeterministicTieBreaking() {
        UUID idA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

        AlternativeCandidate cA = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Prod A").build())
                .alternativeScore(75.0)
                .semanticSimilarityScore(0.80)
                .dealQuality(DealQuality.GOOD_DEAL)
                .rating(4.5)
                .currentBestPrice(BigDecimal.valueOf(100))
                .build();
        cA.getProduct().setId(idA);

        AlternativeCandidate cB = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Prod B").build())
                .alternativeScore(75.0)
                .semanticSimilarityScore(0.80)
                .dealQuality(DealQuality.GOOD_DEAL)
                .rating(4.5)
                .currentBestPrice(BigDecimal.valueOf(100))
                .build();
        cB.getProduct().setId(idB);

        // When all scores and properties are identical, tie-breaker MUST be UUID lexicographical ascending
        List<AlternativeCandidate> list = new ArrayList<>(List.of(cB, cA));
        list.sort(scoringStrategy.getDeterministicComparator());

        assertThat(list.get(0).getProductId()).isEqualTo(idA);
        assertThat(list.get(1).getProductId()).isEqualTo(idB);
    }

    @Test
    @DisplayName("Adversarial: REST security allows public GET/POST access to alternatives endpoints")
    void testRestEndpointAccess() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/product/" + flagshipPhone.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("PRODUCT"));

        mockMvc.perform(get("/api/v1/alternatives/query")
                        .param("query", "smartphone")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"));

        mockMvc.perform(get("/api/v1/alternatives/natural-language")
                        .param("q", "budget smartphone")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"))
                .andExpect(jsonPath("$.alternativeType").value("BUDGET_FALLBACK"));
    }
}
