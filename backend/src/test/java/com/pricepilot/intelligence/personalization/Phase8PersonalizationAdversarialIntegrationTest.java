package com.pricepilot.intelligence.personalization;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceRepository;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizationScorer;
import com.pricepilot.intelligence.personalization.scoring.PersonalizationResult;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalServiceImpl;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Phase8PersonalizationAdversarialIntegrationTest {

    @Autowired
    private UserShoppingPreferenceRepository preferenceRepository;

    @Autowired
    private UserShoppingPreferenceService preferenceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserInteractionEventRepository eventRepository;

    @Autowired
    private BehavioralSignalService behavioralSignalService;

    private DefaultPersonalizationScorer scorer;
    private UserEntity userA;
    private UserEntity userB;
    private SellerEntity testSeller;
    private ProductEntity samplePhone;
    private ProductEntity sampleLaptop;

    @BeforeEach
    void setUp() {
        scorer = new DefaultPersonalizationScorer();

        userA = userRepository.findByEmail("phase8_userA@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email("phase8_userA@example.com")
                        .password("SecurePass123!")
                        .firstName("Shopper")
                        .lastName("Alpha")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build()));

        userB = userRepository.findByEmail("phase8_userB@example.com")
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email("phase8_userB@example.com")
                        .password("SecurePass123!")
                        .firstName("Shopper")
                        .lastName("Beta")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build()));

        samplePhone = productRepository.save(ProductEntity.builder()
                .name("Phase8 Alpha Phone " + UUID.randomUUID())
                .brand("Apple")
                .category("Smartphones")
                .description("Flagship smartphone")
                .archived(false)
                .build());

        sampleLaptop = productRepository.save(ProductEntity.builder()
                .name("Phase8 Pro Laptop " + UUID.randomUUID())
                .brand("Dell")
                .category("Laptops")
                .description("Workstation laptop")
                .archived(false)
                .build());
    }

    // =========================================================================
    // 1. PREFERENCE DOMAIN & DATABASE CONSTRAINTS
    // =========================================================================

    @Test
    @DisplayName("CHECK 1: Database unique constraint enforces one preference record per user")
    void testUniquePreferenceConstraintPerUser() {
        preferenceService.resetPreferences(userA.getId());

        UserShoppingPreferenceEntity pref1 = UserShoppingPreferenceEntity.builder()
                .userId(userA.getId())
                .dealSensitivity(DealSensitivity.HIGH)
                .build();
        preferenceRepository.saveAndFlush(pref1);

        UserShoppingPreferenceEntity pref2 = UserShoppingPreferenceEntity.builder()
                .userId(userA.getId())
                .dealSensitivity(DealSensitivity.LOW)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            preferenceRepository.saveAndFlush(pref2);
        });

        // Cleanup
        preferenceService.resetPreferences(userA.getId());
    }

    @Test
    @DisplayName("CHECK 1b: UserEntity has NO preference-specific fields (clean architectural boundary)")
    void testUserEntityArchitecturalBoundary() {
        Field[] fields = UserEntity.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("preference"), "UserEntity must not contain preference fields: " + name);
            assertFalse(name.contains("budget"), "UserEntity must not contain budget fields: " + name);
            assertFalse(name.contains("brand"), "UserEntity must not contain brand fields: " + name);
            assertFalse(name.contains("category"), "UserEntity must not contain category fields: " + name);
            assertFalse(name.contains("dealsensitivity"), "UserEntity must not contain dealSensitivity: " + name);
        }
    }

    @Test
    @DisplayName("CHECK 1c: Cold-start default behavior and input validation boundaries")
    void testColdStartAndValidationBoundaries() {
        preferenceService.resetPreferences(userA.getId());

        // Cold-start default
        UserShoppingPreferenceDTO defaults = preferenceService.getPreferences(userA.getId());
        assertNotNull(defaults);
        assertEquals(userA.getId(), defaults.getUserId());
        assertEquals(DealSensitivity.MEDIUM, defaults.getDealSensitivity());
        assertEquals(PriceSensitivity.MEDIUM, defaults.getPriceSensitivity());
        assertEquals(AvailabilityPreference.ALL, defaults.getAvailabilityPreference());
        assertTrue(defaults.getPreferredCategories().isEmpty());
        assertTrue(defaults.getPreferredBrands().isEmpty());

        // Validation boundary: minBudget > maxBudget
        UpdateShoppingPreferenceRequest invalidRequest = UpdateShoppingPreferenceRequest.builder()
                .minBudget(BigDecimal.valueOf(1500))
                .maxBudget(BigDecimal.valueOf(500))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            preferenceService.updatePreferences(userA.getId(), invalidRequest);
        });
        assertTrue(ex.getMessage().contains("Minimum budget cannot exceed maximum budget"));
    }

    // =========================================================================
    // 2. CROSS-USER SECURITY & STRICT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("CHECK 2: Strict cross-user isolation: User A updating preferences never impacts User B")
    void testCrossUserPreferenceIsolation() {
        preferenceService.resetPreferences(userA.getId());
        preferenceService.resetPreferences(userB.getId());

        // Update User A
        preferenceService.updatePreferences(userA.getId(), UpdateShoppingPreferenceRequest.builder()
                .preferredCategories(Set.of("Smartphones"))
                .preferredBrands(Set.of("Apple"))
                .dealSensitivity(DealSensitivity.HIGH)
                .minBudget(BigDecimal.valueOf(800))
                .maxBudget(BigDecimal.valueOf(1200))
                .build());

        // Verify User B is completely untouched and on defaults
        UserShoppingPreferenceDTO prefB = preferenceService.getPreferences(userB.getId());
        assertEquals(userB.getId(), prefB.getUserId());
        assertTrue(prefB.getPreferredCategories().isEmpty());
        assertTrue(prefB.getPreferredBrands().isEmpty());
        assertEquals(DealSensitivity.MEDIUM, prefB.getDealSensitivity());
        assertNull(prefB.getMinBudget());
        assertNull(prefB.getMaxBudget());

        // Reset User A
        preferenceService.resetPreferences(userA.getId());
        UserShoppingPreferenceDTO afterResetA = preferenceService.getPreferences(userA.getId());
        assertTrue(afterResetA.getPreferredCategories().isEmpty());

        // User B still untouched
        UserShoppingPreferenceDTO prefBSecondCheck = preferenceService.getPreferences(userB.getId());
        assertEquals(DealSensitivity.MEDIUM, prefBSecondCheck.getDealSensitivity());
    }

    // =========================================================================
    // 3. PERSONALIZATION SCORING VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("CHECK 3: Verify all documented scoring contributions individually")
    void testIndividualScoringContributions() {
        ProductPriceResponseDTO inStockDiscountedPrice = ProductPriceResponseDTO.builder()
                .currentPrice(BigDecimal.valueOf(900))
                .originalPrice(BigDecimal.valueOf(1200))
                .discountPercentage(BigDecimal.valueOf(25)) // 25% discount
                .build();

        ProductResponseDTO testProduct = ProductResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("Apple iPhone Pro")
                .category("Smartphones")
                .brand("Apple")
                .prices(List.of(inStockDiscountedPrice))
                .build();

        com.pricepilot.intelligence.recommendation.dto.ProductScore baseScore =
                new com.pricepilot.intelligence.recommendation.dto.ProductScore(
                        testProduct.getId(), testProduct.getName(), 80.0, 75.0, 85.0, 80.0, Map.of(), "TOP"
                );

        // 1. Preferred Category (+12.0)
        UserShoppingPreferenceEntity categoryPref = UserShoppingPreferenceEntity.builder()
                .preferredCategories(Set.of("Smartphones"))
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resCat = scorer.scorePersonalization(testProduct, baseScore, categoryPref, UserShoppingSignals.neutral());
        assertEquals(12.0, resCat.getPersonalizationContribution(), 0.001);
        assertEquals(92.0, resCat.getFinalScore(), 0.001);
        assertTrue(resCat.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_CATEGORY));

        // 2. Preferred Brand (+10.0)
        UserShoppingPreferenceEntity brandPref = UserShoppingPreferenceEntity.builder()
                .preferredBrands(Set.of("Apple"))
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resBrand = scorer.scorePersonalization(testProduct, baseScore, brandPref, UserShoppingSignals.neutral());
        assertEquals(10.0, resBrand.getPersonalizationContribution(), 0.001);
        assertTrue(resBrand.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));

        // 3. Budget Match (+8.0)
        UserShoppingPreferenceEntity budgetMatchPref = UserShoppingPreferenceEntity.builder()
                .minBudget(BigDecimal.valueOf(800))
                .maxBudget(BigDecimal.valueOf(1000))
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resBudget = scorer.scorePersonalization(testProduct, baseScore, budgetMatchPref, UserShoppingSignals.neutral());
        assertEquals(8.0, resBudget.getPersonalizationContribution(), 0.001);
        assertTrue(resBudget.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.WITHIN_BUDGET));

        // 4. Exceeds Budget (-10.0)
        UserShoppingPreferenceEntity budgetExceededPref = UserShoppingPreferenceEntity.builder()
                .maxBudget(BigDecimal.valueOf(800)) // Product price is 900
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resExceed = scorer.scorePersonalization(testProduct, baseScore, budgetExceededPref, UserShoppingSignals.neutral());
        assertEquals(-10.0, resExceed.getPersonalizationContribution(), 0.001);
        assertEquals(70.0, resExceed.getFinalScore(), 0.001);
        assertTrue(resExceed.getPersonalizationTradeOffs().stream().anyMatch(e -> e.getType() == EvidenceType.EXCEEDS_BUDGET));

        // 5. Rating Threshold Met (+6.0)
        UserShoppingPreferenceEntity ratingPref = UserShoppingPreferenceEntity.builder()
                .minRating(4.0)
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resRating = scorer.scorePersonalization(testProduct, baseScore, ratingPref, UserShoppingSignals.neutral());
        assertEquals(6.0, resRating.getPersonalizationContribution(), 0.001);
        assertTrue(resRating.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.RATING_CRITERIA_MET));

        // 6. High Deal Sensitivity (+8.0 on >=15% discount)
        UserShoppingPreferenceEntity dealPref = UserShoppingPreferenceEntity.builder()
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
        PersonalizationResult resDeal = scorer.scorePersonalization(testProduct, baseScore, dealPref, UserShoppingSignals.neutral());
        assertEquals(8.0, resDeal.getPersonalizationContribution(), 0.001);
        assertTrue(resDeal.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.DEAL_SENSITIVITY_MATCH));

        // 7. In-Stock (+4.0)
        UserShoppingPreferenceEntity inStockPref = UserShoppingPreferenceEntity.builder()
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .build();
        PersonalizationResult resInStock = scorer.scorePersonalization(testProduct, baseScore, inStockPref, UserShoppingSignals.neutral());
        assertEquals(4.0, resInStock.getPersonalizationContribution(), 0.001);

        // 8. Out-of-Stock under IN_STOCK_ONLY (-20.0)
        ProductResponseDTO oosProduct = ProductResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("OOS Product")
                .prices(List.of())
                .build();
        PersonalizationResult resOOS = scorer.scorePersonalization(oosProduct, baseScore, inStockPref, UserShoppingSignals.neutral());
        assertEquals(-20.0, resOOS.getPersonalizationContribution(), 0.001);
        assertEquals(60.0, resOOS.getFinalScore(), 0.001);

        // 9. Behavioral Affinity (Category up to +6.0, Brand up to +4.0)
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .categoryAffinity(Map.of("smartphones", 1.0))
                .brandAffinity(Map.of("apple", 0.5))
                .build();
        PersonalizationResult resBehavior = scorer.scorePersonalization(testProduct, baseScore, null, signals);
        assertEquals(8.0, resBehavior.getPersonalizationContribution(), 0.001);
        assertTrue(resBehavior.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.BEHAVIORAL_AFFINITY));
    }

    @Test
    @DisplayName("CHECK 3b: Deterministic 5-tier tie-breaking and candidate shuffling stability across 50 iterations")
    void testDeterministicTieBreakingStability() {
        UUID id1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID id2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID id3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ProductResponseDTO prod1 = ProductResponseDTO.builder().id(id1).name("Prod 1").build();
        ProductResponseDTO prod2 = ProductResponseDTO.builder().id(id2).name("Prod 2").build();
        ProductResponseDTO prod3 = ProductResponseDTO.builder().id(id3).name("Prod 3").build();

        PersonalizationResult r1 = PersonalizationResult.builder().baseScore(80.0).personalizationContribution(10.0).finalScore(90.0).build();
        PersonalizationResult r2 = PersonalizationResult.builder().baseScore(80.0).personalizationContribution(10.0).finalScore(90.0).build();
        PersonalizationResult r3 = PersonalizationResult.builder().baseScore(80.0).personalizationContribution(10.0).finalScore(90.0).build();

        Map<UUID, PersonalizationResult> resultMap = Map.of(id1, r1, id2, r2, id3, r3);

        List<ProductResponseDTO> baseline = Arrays.asList(prod1, prod2, prod3);
        List<ProductResponseDTO> expected = new ArrayList<>(baseline);
        expected.sort(scorer.getDeterministicPersonalizedComparator(resultMap));

        // Run 50 iterations with randomly shuffled inputs
        for (int i = 0; i < 50; i++) {
            List<ProductResponseDTO> shuffled = new ArrayList<>(baseline);
            Collections.shuffle(shuffled);
            shuffled.sort(scorer.getDeterministicPersonalizedComparator(resultMap));

            assertEquals(expected.get(0).getId(), shuffled.get(0).getId());
            assertEquals(expected.get(1).getId(), shuffled.get(1).getId());
            assertEquals(expected.get(2).getId(), shuffled.get(2).getId());
        }
    }

    // =========================================================================
    // 4. BEHAVIORAL SIGNAL INTEGRITY & BOUNDARIES
    // =========================================================================

    @Test
    @DisplayName("CHECK 4: Behavioral signal deduplication boundary conditions (179s suppressed, 181s counted)")
    void testBehavioralSignalDeduplicationBoundaries() {
        LocalDateTime now = LocalDateTime.now();

        UserInteractionEventEntity ev1 = UserInteractionEventEntity.builder()
                .id(UUID.randomUUID())
                .user(userA)
                .product(samplePhone)
                .interactionType(InteractionType.PRODUCT_VIEW)
                .createdAt(now.minusSeconds(200))
                .build();

        UserInteractionEventEntity ev2 = UserInteractionEventEntity.builder()
                .id(UUID.randomUUID())
                .user(userA)
                .product(samplePhone)
                .interactionType(InteractionType.PRODUCT_VIEW)
                .createdAt(now.minusSeconds(21)) // 179s difference -> suppressed
                .build();

        BehavioralSignalServiceImpl serviceImpl = new BehavioralSignalServiceImpl(null);
        UserShoppingSignals signals = serviceImpl.aggregateSignalsFromEvents(userA.getId(), List.of(ev2, ev1));

        // Only 1 view should be counted
        assertEquals(1, signals.getTotalInteractions());

        // Event 181 seconds apart -> should count
        UserInteractionEventEntity ev3 = UserInteractionEventEntity.builder()
                .id(UUID.randomUUID())
                .user(userA)
                .product(samplePhone)
                .interactionType(InteractionType.PRODUCT_VIEW)
                .createdAt(now.minusSeconds(19)) // 181s from ev1
                .build();

        UserShoppingSignals signalsCounted = serviceImpl.aggregateSignalsFromEvents(userA.getId(), List.of(ev3, ev1));
        assertEquals(2, signalsCounted.getTotalInteractions());
    }

    @Test
    @DisplayName("CHECK 4b: Per-product interaction satiation cap of 5")
    void testPerProductSatiationCap() {
        LocalDateTime now = LocalDateTime.now();
        List<UserInteractionEventEntity> events = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            events.add(UserInteractionEventEntity.builder()
                    .id(UUID.randomUUID())
                    .user(userA)
                    .product(samplePhone)
                    .interactionType(InteractionType.PRODUCT_VIEW)
                    .createdAt(now.minusSeconds(i * 200))
                    .build());
        }

        BehavioralSignalServiceImpl serviceImpl = new BehavioralSignalServiceImpl(null);
        UserShoppingSignals signals = serviceImpl.aggregateSignalsFromEvents(userA.getId(), events);

        assertEquals(5, signals.getTotalInteractions());
    }

    // =========================================================================
    // 5. EXPLAINABILITY INTEGRITY (ZERO HALLUCINATIONS)
    // =========================================================================

    @Test
    @DisplayName("CHECK 5: Personalization evidence is emitted strictly when factual condition is true")
    void testExplainabilityStrictGrounding() {
        ProductResponseDTO incompleteProduct = ProductResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("Minimal Gadget")
                .category("Accessories")
                .brand("Generic")
                .prices(List.of())
                .build();

        UserShoppingPreferenceEntity prefs = UserShoppingPreferenceEntity.builder()
                .preferredCategories(Set.of("Smartphones"))
                .preferredBrands(Set.of("Apple"))
                .minRating(5.5)
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1000))
                .dealSensitivity(DealSensitivity.HIGH)
                .build();

        com.pricepilot.intelligence.recommendation.dto.ProductScore baseScore =
                new com.pricepilot.intelligence.recommendation.dto.ProductScore(
                        incompleteProduct.getId(), incompleteProduct.getName(), 75.0, 75.0, 75.0, 75.0, Map.of(), "TOP"
                );

        PersonalizationResult result = scorer.scorePersonalization(incompleteProduct, baseScore, prefs, UserShoppingSignals.neutral());

        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_CATEGORY));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.WITHIN_BUDGET));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.EXCEEDS_BUDGET));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.RATING_CRITERIA_MET));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.DEAL_SENSITIVITY_MATCH));
        assertFalse(result.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.BEHAVIORAL_AFFINITY));
        assertEquals(0.0, result.getPersonalizationContribution(), 0.001);
        assertEquals(75.0, result.getFinalScore(), 0.001);
    }

    // =========================================================================
    // 6. MULTI-THREADED CONCURRENCY & CACHE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("CHECK 6: Concurrent multi-threaded preference access ensures zero crosstalk")
    void testConcurrentPreferenceAccess() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final boolean isUserA = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                try {
                    UUID targetUser = isUserA ? userA.getId() : userB.getId();
                    UserShoppingPreferenceDTO prefs = preferenceService.getPreferences(targetUser);
                    return prefs != null && prefs.getUserId().equals(targetUser);
                } finally {
                    latch.countDown();
                }
            }));
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        for (Future<Boolean> future : futures) {
            assertTrue(future.get());
        }
        executor.shutdown();
    }
}
