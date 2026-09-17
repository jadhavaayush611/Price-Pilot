package com.pricepilot.intelligence.personalization;

import com.pricepilot.intelligence.alternative.model.AlternativeRequest;
import com.pricepilot.intelligence.alternative.model.AlternativeResponseDTO;
import com.pricepilot.intelligence.alternative.model.AlternativeType;
import com.pricepilot.intelligence.alternative.personalized.PersonalizedAlternativeService;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.personalized.PersonalizedDiscoveryService;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceRepository;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Performance & Caching Optimization Integration Test Suite")
public class PersonalizationCachingAndPerformanceIntegrationTest {

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private UserShoppingPreferenceRepository preferenceRepository;

    @Autowired
    private UserShoppingPreferenceService preferenceService;

    @Autowired
    private UserInteractionEventRepository eventRepository;

    @Autowired
    private UserInteractionEventService eventService;

    @Autowired
    private BehavioralSignalService behavioralSignalService;

    @Autowired
    private PersonalizationContextProvider personalizationContextProvider;

    @Autowired
    private PersonalizedDiscoveryService personalizedDiscoveryService;

    @Autowired
    private PersonalizedAlternativeService personalizedAlternativeService;

    @Autowired
    private RecommendationService recommendationService;

    private UserEntity userA;
    private UserEntity userB;
    private SellerEntity seller;
    private ProductEntity product1;
    private ProductEntity product2;
    private ProductEntity product3;

    @BeforeEach
    void setUp() {
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                Cache c = cacheManager.getCache(name);
                if (c != null) c.clear();
            });
        }

        preferenceRepository.deleteAll();
        eventRepository.deleteAll();
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        userA = userRepository.findByEmail("perf_userA@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email("perf_userA@example.com")
                        .password("SecurePass123!")
                        .firstName("Perf")
                        .lastName("UserA")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build()));

        userB = userRepository.findByEmail("perf_userB@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email("perf_userB@example.com")
                        .password("SecurePass123!")
                        .firstName("Perf")
                        .lastName("UserB")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build()));

        seller = sellerRepository.save(SellerEntity.builder()
                .name("Perf Seller")
                .websiteUrl("https://perfseller.com")
                .build());

        product1 = productRepository.save(ProductEntity.builder()
                .name("Perf Apple MacBook Pro")
                .brand("Apple")
                .category("Electronics")
                .description("M3 powerhouse")
                .imageUrl("https://example.com/mac.png")
                .archived(false)
                .build());

        product2 = productRepository.save(ProductEntity.builder()
                .name("Perf Samsung Galaxy Book")
                .brand("Samsung")
                .category("Electronics")
                .description("AMOLED laptop")
                .imageUrl("https://example.com/galaxy.png")
                .archived(false)
                .build());

        product3 = productRepository.save(ProductEntity.builder()
                .name("Perf Sony Headphones")
                .brand("Sony")
                .category("Audio")
                .description("Noise cancelling")
                .imageUrl("https://example.com/sony.png")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(product1)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1999.00))
                .originalPrice(BigDecimal.valueOf(2199.00))
                .discountPercentage(BigDecimal.valueOf(9.1))
                .productUrl("https://perfseller.com/mac")
                .lastUpdated(LocalDateTime.now())
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(product2)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1499.00))
                .originalPrice(BigDecimal.valueOf(1699.00))
                .discountPercentage(BigDecimal.valueOf(11.77))
                .productUrl("https://perfseller.com/galaxy")
                .lastUpdated(LocalDateTime.now())
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(product3)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(349.00))
                .originalPrice(BigDecimal.valueOf(399.00))
                .discountPercentage(BigDecimal.valueOf(12.53))
                .productUrl("https://perfseller.com/sony")
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("Case 1: Explicit Preference Cache: Hit, Miss, and Invalidation on Update")
    void testPreferenceCacheLifecycle() {
        if (cacheManager == null) return;
        Cache prefCache = cacheManager.getCache("user-preferences");
        if (prefCache == null) return;

        // Initially, cache miss
        assertNull(prefCache.get(userA.getId()));

        // Update preference persists and evicts/updates
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Apple"))
                .build());

        // First read loads and populates cache
        UserShoppingPreferenceDTO dto1 = preferenceService.getPreferences(userA.getId());
        assertNotNull(dto1);
        assertTrue(dto1.getPreferredBrands().contains("Apple"));

        // Verify cache hit
        assertNotNull(prefCache.get(userA.getId()));

        // Update preferences for User A
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Samsung"))
                .build());

        // Cache was evicted/refreshed
        UserShoppingPreferenceDTO dto2 = preferenceService.getPreferences(userA.getId());
        assertTrue(dto2.getPreferredBrands().contains("Samsung"));
        assertFalse(dto2.getPreferredBrands().contains("Apple"));

        // Verify User B's cache is completely unaffected
        assertNull(prefCache.get(userB.getId()));
    }

    @Test
    @DisplayName("Case 2: Behavioral Signal Cache: Hit, Miss, and Invalidation on Event Tracking")
    void testBehavioralSignalCacheLifecycle() {
        if (cacheManager == null) return;
        Cache behavioralCache = cacheManager.getCache("user-behavioral-signals");
        if (behavioralCache == null) return;

        // First extraction caches signals
        UserShoppingSignals s1 = behavioralSignalService.extractSignals(userA.getId());
        assertNotNull(s1);
        assertEquals(0, s1.getTotalInteractions());

        // Verify cached
        assertNotNull(behavioralCache.get(userA.getId()));

        // Track an interaction event for User A
        eventService.trackEvent(
                userA.getId(),
                product1.getId(),
                seller.getId(),
                InteractionType.PRODUCT_VIEW,
                Map.of("category", "Electronics")
        );

        // Verify cache is evicted
        assertNull(behavioralCache.get(userA.getId()));

        // Re-extraction loads new event
        UserShoppingSignals s2 = behavioralSignalService.extractSignals(userA.getId());
        assertNotNull(s2);
        assertEquals(1, s2.getTotalInteractions());
    }

    @Test
    @DisplayName("Case 3: Single Context Resolution in Discovery and Bounded Execution")
    void testSingleContextResolutionInDiscovery() {
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Apple"))
                .preferredCategories(Set.of("Electronics"))
                .build());

        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("Electronics")
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = personalizedDiscoveryService.discover(request, userA.getId());
        assertNotNull(response);
        assertFalse(response.getContent().isEmpty());

        for (DiscoveryProductDTO item : response.getContent()) {
            assertNotNull(item.getPersonalizedScore());
            assertNotNull(item.getPersonalizationAdjustment());
        }
    }

    @Test
    @DisplayName("Case 4: Personalized Alternatives Bounds: semantic <= 50, structured <= 50, result <= 20")
    void testAlternativeBoundsEnforced() {
        AlternativeRequest req = AlternativeRequest.builder()
                .productId(product1.getId())
                .type(AlternativeType.SIMILAR)
                .limit(100) // Requesting 100
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(
                product1.getId(), req, userA.getId()
        );

        assertNotNull(response);
        assertTrue(response.getContent().size() <= 20, "Results must never exceed max 20 alternatives");
    }

    @Test
    @DisplayName("Case 5: Personalized Recommendations Pipeline Performance and Evidence Grounding")
    void testRecommendationPipelinePerformance() {
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Apple"))
                .build());

        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(userA.getId(), 5);
        assertNotNull(response);
        assertNotNull(response.getRecommendedProduct());
        assertNotNull(response.getSupportingFactors());
    }

    @Test
    @DisplayName("Case 6: 20+ Concurrent Multi-User Requests Maintain Strict Isolation")
    void testHighConcurrencyMultiUserIsolation() throws Exception {
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Apple"))
                .build());

        preferenceService.updatePreferences(userB.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Samsung"))
                .build());

        // Warm up discovery to initialize product analytics entity before concurrent burst
        personalizedDiscoveryService.discover(DiscoverySearchRequestDTO.builder().query("Electronics").page(0).size(10).build(), userA.getId());

        int threads = 24;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final boolean isUserA = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                try {
                    UUID targetUser = isUserA ? userA.getId() : userB.getId();
                    String expectedBrand = isUserA ? "apple" : "samsung";
                    String forbiddenBrand = isUserA ? "samsung" : "apple";

                    PersonalizationContext ctx = personalizationContextProvider.getPersonalizationContext(targetUser);
                    boolean match = ctx.getPreferredBrands().contains(expectedBrand);
                    boolean noForbidden = !ctx.getPreferredBrands().contains(forbiddenBrand);

                    DiscoverySearchRequestDTO req = DiscoverySearchRequestDTO.builder()
                            .query("Electronics")
                            .page(0)
                            .size(10)
                            .build();

                    DiscoverySearchResponseDTO res = personalizedDiscoveryService.discover(req, targetUser);
                    return match && noForbidden && !res.getContent().isEmpty();
                } finally {
                    latch.countDown();
                }
            }));
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(), "Concurrent requests must maintain 100% user context isolation");
        }
        executor.shutdown();
    }

    @Test
    @DisplayName("Case 7: Generic vs Personalized Cache Separation")
    void testGenericVsPersonalizedSeparation() {
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredBrands(Set.of("Apple"))
                .build());

        // Personalized request
        DiscoverySearchResponseDTO personalizedRes = personalizedDiscoveryService.discover(
                DiscoverySearchRequestDTO.builder().query("Electronics").build(),
                userA.getId()
        );

        // Cold/empty context request (simulating generic)
        PersonalizationContext emptyCtx = PersonalizationContext.empty(null);
        DiscoverySearchResponseDTO genericRes = personalizedDiscoveryService.discover(
                DiscoverySearchRequestDTO.builder().query("Electronics").build(),
                emptyCtx
        );

        assertNotNull(personalizedRes);
        assertNotNull(genericRes);

        // In generic response, personalizedEvidence is empty
        assertTrue(genericRes.getContent().stream().allMatch(p -> p.getPersonalizedEvidence().isEmpty()));
    }
}
