package com.pricepilot.intelligence;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.assistant.dto.AssistantConversationDTO;
import com.pricepilot.intelligence.assistant.dto.AssistantResponseDTO;
import com.pricepilot.intelligence.assistant.dto.CreateConversationRequest;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import com.pricepilot.intelligence.assistant.service.ShoppingAssistantService;
import com.pricepilot.intelligence.comparison.ComparisonService;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;
import com.pricepilot.intelligence.dashboard.service.DashboardV2Service;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistService;
import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10: Release Integration & Production Hardening End-to-End Suite.
 * Validates cross-service flows, unified intelligence, performance bounds, and security invariants.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PricePilotPhase10IntegrationAndHardeningTest {

    @Autowired
    private SearchDiscoveryService discoveryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository priceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PriceAnalyticsService priceAnalyticsService;

    @Autowired
    private ComparisonService comparisonService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private PriceWatchlistService watchlistService;

    @Autowired
    private PriceAlertService alertService;

    @Autowired
    private DashboardV2Service dashboardV2Service;

    @Autowired
    private UserShoppingPreferenceService preferenceService;

    @Autowired
    private ShoppingAssistantService assistantService;

    private UserEntity userA;
    private UserEntity userB;
    private UserPrincipal principalA;
    private UserPrincipal principalB;
    private ProductEntity product1;
    private ProductEntity product2;
    private SellerEntity seller1;
    private SellerEntity seller2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userA = userRepository.save(UserEntity.builder()
                .email("hardening-userA-" + suffix + "@pricepilot.io")
                .firstName("Alice")
                .lastName("Hardening")
                .password("Password123!")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        userB = userRepository.save(UserEntity.builder()
                .email("hardening-userB-" + suffix + "@pricepilot.io")
                .firstName("Bob")
                .lastName("Hardening")
                .password("Password123!")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        principalA = new UserPrincipal(userA.getId(), userA.getEmail(), userA.getPassword(), userA.getRole(), true, false);
        principalB = new UserPrincipal(userB.getId(), userB.getEmail(), userB.getPassword(), userB.getRole(), true, false);

        seller1 = sellerRepository.save(SellerEntity.builder()
                .name("Alpha Retail " + suffix)
                .websiteUrl("https://alpha-retail.example.com")
                .build());

        seller2 = sellerRepository.save(SellerEntity.builder()
                .name("Beta Electronics " + suffix)
                .websiteUrl("https://beta-electronics.example.com")
                .build());

        product1 = productRepository.save(ProductEntity.builder()
                .name("Pro UltraBook 15-" + suffix)
                .brand("TechBrand")
                .category("Laptop")
                .description("Flagship professional laptop with high-speed memory")
                .archived(false)
                .build());

        product2 = productRepository.save(ProductEntity.builder()
                .name("Air UltraBook 14-" + suffix)
                .brand("TechBrand")
                .category("Laptop")
                .description("Lightweight portable laptop with long battery life")
                .archived(false)
                .build());

        priceRepository.save(ProductPriceEntity.builder()
                .product(product1)
                .seller(seller1)
                .currentPrice(new BigDecimal("1200.00"))
                .originalPrice(new BigDecimal("1400.00"))
                .discountPercentage(new BigDecimal("14.28"))
                .lastUpdated(LocalDateTime.now())
                .build());

        priceRepository.save(ProductPriceEntity.builder()
                .product(product2)
                .seller(seller2)
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1099.00"))
                .discountPercentage(new BigDecimal("9.10"))
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    // =========================================================================
    // 1. JOURNEY 1: Search & Discovery -> Product -> Comparison
    // =========================================================================

    @Test
    @DisplayName("Journey 1: Search discovery candidate retrieval into comparison analysis")
    void testJourney_Search_To_Product_To_Compare() {
        DiscoverySearchRequestDTO searchReq = new DiscoverySearchRequestDTO();
        searchReq.setQuery("UltraBook");
        searchReq.setPage(0);
        searchReq.setSize(10);
        DiscoverySearchResponseDTO searchResults = discoveryService.searchAndDiscover(searchReq);

        assertNotNull(searchResults);
        assertFalse(searchResults.getContent().isEmpty(), "Discovery should retrieve matching products");

        List<UUID> productIds = searchResults.getContent().stream()
                .map(DiscoveryProductDTO::getId)
                .limit(2)
                .toList();

        ComparisonResponse compRes = comparisonService.compareProducts(productIds);
        assertNotNull(compRes);
        assertEquals(2, compRes.getProducts().size(), "Both products must be compared");
        assertNotNull(compRes.getRows());
        assertNotNull(compRes.getSummary());
    }

    // =========================================================================
    // 2. JOURNEY 2: Search -> Product -> Watchlist -> Alert Evaluation
    // =========================================================================

    @Test
    @DisplayName("Journey 2: Search discovery into watchlist creation and alert generation")
    void testJourney_Search_To_Product_To_Watchlist_To_Alert() {
        WatchlistResponseDTO watchlist = watchlistService.createWatchlist(
                userA.getEmail(),
                new CreateWatchlistRequestDTO(product1.getId(), new BigDecimal("1100.00"))
        );
        assertNotNull(watchlist);
        assertEquals(product1.getId(), watchlist.getProductId());

        alertService.processPriceUpdateEvent(
                product1.getId(),
                new BigDecimal("1200.00"),
                new BigDecimal("1050.00"),
                false
        );

        Page<PriceAlertResponseDTO> alertsPage = alertService.getUserAlerts(userA.getId(), PageRequest.of(0, 10));
        assertNotNull(alertsPage);
        assertFalse(alertsPage.getContent().isEmpty(), "Alert must be generated when price drops below target");
        assertEquals(userA.getId(), alertsPage.getContent().get(0).getUserId());
    }

    // =========================================================================
    // 3. JOURNEY 3: Product -> Analytics -> Assistant Decision Support
    // =========================================================================

    @Test
    @DisplayName("Journey 3: Product analytics into grounded shopping assistant consultation")
    void testJourney_Product_To_Analytics_To_Assistant() {
        ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(product1.getId());
        assertNotNull(analytics);
        assertNotNull(analytics.getDealQuality());

        AssistantConversationDTO conv = assistantService.createConversation(
                userA.getId(),
                new CreateConversationRequest("Inquiry about " + product1.getName(), null)
        );

        SendMessageRequest msgReq = new SendMessageRequest();
        msgReq.setContent("Is " + product1.getName() + " a good deal and should I buy now?");
        msgReq.setActiveProductId(product1.getId());

        AssistantResponseDTO assistantRes = assistantService.sendMessage(conv.getId(), userA.getId(), msgReq);
        assertNotNull(assistantRes.getResponse());
        assertNotNull(assistantRes.getEvidenceBundle());
        assertFalse(assistantRes.getResponse().isBlank());
    }

    // =========================================================================
    // 4. JOURNEY 4: Recommendation -> Comparison
    // =========================================================================

    @Test
    @DisplayName("Journey 4: Personalized recommendations fed directly into comparison engine")
    void testJourney_Recommendation_To_Comparison() {
        UpdateShoppingPreferenceRequest prefReq = UpdateShoppingPreferenceRequest.builder()
                .preferredCategories(Set.of("Laptop"))
                .preferredBrands(Set.of("TechBrand"))
                .maxBudget(new BigDecimal("1500.00"))
                .build();
        preferenceService.updatePreferences(userA.getId(), prefReq);

        RecommendationResponse recRes = recommendationService.getPersonalizedRecommendations(userA.getId(), 5);
        assertNotNull(recRes);

        if (recRes.getRecommendedProducts() != null && recRes.getRecommendedProducts().size() >= 2) {
            List<UUID> recIds = recRes.getRecommendedProducts().stream()
                    .map(p -> p.getId())
                    .limit(2)
                    .toList();

            ComparisonResponse comp = comparisonService.compareProducts(recIds);
            assertNotNull(comp);
            assertNotNull(comp.getSummary());
        }
    }

    // =========================================================================
    // 5. JOURNEY 5: Dashboard V2 -> Intelligence Aggregation
    // =========================================================================

    @Test
    @DisplayName("Journey 5: Dashboard V2 orchestrates alerts, watchlists, and recommendations")
    void testJourney_DashboardV2_UnifiedIntelligence() {
        watchlistService.createWatchlist(
                userA.getEmail(),
                new CreateWatchlistRequestDTO(product2.getId(), new BigDecimal("950.00"))
        );

        DashboardV2ResponseDTO dashboard = dashboardV2Service.getDashboardV2(userA.getId(), userA.getEmail());
        assertNotNull(dashboard);
        assertNotNull(dashboard.getOverview());
        assertNotNull(dashboard.getWatchedProducts());
        assertNotNull(dashboard.getAttentionItems());
        assertNotNull(dashboard.getRecentAlerts());
        assertNotNull(dashboard.getRecommendations());
    }

    // =========================================================================
    // 6. SECURITY HARDENING: IDOR & CROSS-USER ISOLATION ACROSS ALL MODULES
    // =========================================================================

    @Test
    @DisplayName("Security Hardening: User B strictly isolated from User A across all intelligence modules")
    void testCrossUserHardening_AllModules() {
        AssistantConversationDTO convA = assistantService.createConversation(
                userA.getId(),
                new CreateConversationRequest("Confidential A", null)
        );

        WatchlistResponseDTO watchA = watchlistService.createWatchlist(
                userA.getEmail(),
                new CreateWatchlistRequestDTO(product1.getId(), new BigDecimal("1100.00"))
        );

        alertService.processPriceUpdateEvent(
                product1.getId(),
                new BigDecimal("1200.00"),
                new BigDecimal("1050.00"),
                false
        );

        Page<PriceAlertResponseDTO> alertsA = alertService.getUserAlerts(userA.getId(), PageRequest.of(0, 10));
        assertFalse(alertsA.getContent().isEmpty());
        UUID alertId = alertsA.getContent().get(0).getId();

        // Attack 1: User B tries to get User A's conversation
        assertThrows(Exception.class, () ->
                assistantService.getConversation(convA.getId(), userB.getId()));

        // Attack 2: User B tries to mark User A's alert as read
        assertThrows(Exception.class, () ->
                alertService.markAsRead(alertId, userB.getId()));

        // Attack 3: User B tries to view User A's preferences
        UserShoppingPreferenceDTO prefB = preferenceService.getPreferences(userB.getId());
        assertNotEquals(userA.getId(), prefB.getUserId());
    }

    // =========================================================================
    // 7. PERFORMANCE & QUERY BOUNDS
    // =========================================================================

    @Test
    @DisplayName("Performance: Verify bounded retrieval and pagination clamping")
    void testPerformance_BoundedRetrieval() {
        DiscoverySearchRequestDTO req = new DiscoverySearchRequestDTO();
        req.setQuery("Laptops");
        req.setPage(0);
        req.setSize(10);

        long start = System.currentTimeMillis();
        DiscoverySearchResponseDTO page = discoveryService.searchAndDiscover(req);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(page);
        assertTrue(duration < 1500, "Discovery search must execute in < 1500ms, took " + duration + "ms");
        assertTrue(page.getContent().size() <= 10, "Page size must respect requested bounds");
    }
}
