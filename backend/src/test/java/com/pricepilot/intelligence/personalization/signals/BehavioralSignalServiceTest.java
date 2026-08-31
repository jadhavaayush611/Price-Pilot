package com.pricepilot.intelligence.personalization.signals;

import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.product.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehavioralSignalServiceTest {

    @Mock
    private UserInteractionEventRepository eventRepository;

    private BehavioralSignalServiceImpl signalService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        signalService = new BehavioralSignalServiceImpl(eventRepository);
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Extracts and normalizes category and brand affinity from interactions")
    void testExtractSignalsBasic() {
        ProductEntity phone = new ProductEntity();
        phone.setId(UUID.randomUUID());
        phone.setCategory("Smartphones");
        phone.setBrand("Apple");

        ProductEntity laptop = new ProductEntity();
        laptop.setId(UUID.randomUUID());
        laptop.setCategory("Laptops");
        laptop.setBrand("Dell");

        LocalDateTime now = LocalDateTime.now();
        List<UserInteractionEventEntity> events = List.of(
                createEvent(InteractionType.PRODUCT_SAVE, phone, now.minusMinutes(10)),
                createEvent(InteractionType.PRODUCT_VIEW, phone, now.minusMinutes(5)),
                createEvent(InteractionType.PRODUCT_VIEW, laptop, now.minusMinutes(2))
        );

        when(eventRepository.findByUserIdWithRelations(eq(testUserId), any(Pageable.class)))
                .thenReturn(events);

        UserShoppingSignals signals = signalService.extractSignals(testUserId);

        assertNotNull(signals);
        assertEquals(testUserId, signals.getUserId());
        assertEquals(3, signals.getTotalInteractions());

        // Apple & Smartphones had a SAVE (weight 3.0) + VIEW (weight 1.0) = 4.0 (max) -> normalized to 1.0
        assertEquals(1.0, signals.getCategoryAffinity("Smartphones"));
        assertEquals(1.0, signals.getBrandAffinity("Apple"));

        // Dell & Laptops had VIEW (weight 1.0) / 4.0 = 0.25
        assertEquals(0.25, signals.getCategoryAffinity("Laptops"));
        assertEquals(0.25, signals.getBrandAffinity("Dell"));
    }

    @Test
    @DisplayName("Deduplicates spam and replay attacks within the 3-minute deduplication window")
    void testDeduplicationReplayProtection() {
        ProductEntity phone = new ProductEntity();
        phone.setId(UUID.randomUUID());
        phone.setCategory("Smartphones");
        phone.setBrand("Apple");

        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(20);
        List<UserInteractionEventEntity> events = new ArrayList<>();

        // Add 10 rapid view events within 1 minute of each other (replayed clicks)
        for (int i = 0; i < 10; i++) {
            events.add(createEvent(InteractionType.PRODUCT_VIEW, phone, baseTime.plusSeconds(i * 5)));
        }

        when(eventRepository.findByUserIdWithRelations(eq(testUserId), any(Pageable.class)))
                .thenReturn(events);

        UserShoppingSignals signals = signalService.extractSignals(testUserId);

        // Only the first event should be counted; subsequent 9 events within window suppressed
        assertEquals(1, signals.getTotalInteractions());
        assertEquals(1.0, signals.getCategoryAffinity("Smartphones"));
    }

    @Test
    @DisplayName("Caps repetitive interactions per product at maximum satiation limit")
    void testSatiationCap() {
        ProductEntity phone = new ProductEntity();
        phone.setId(UUID.randomUUID());
        phone.setCategory("Smartphones");
        phone.setBrand("Samsung");

        LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
        List<UserInteractionEventEntity> events = new ArrayList<>();

        // Add 15 spaced out views (each 10 minutes apart, avoiding the 3-min window)
        for (int i = 0; i < 15; i++) {
            events.add(createEvent(InteractionType.PRODUCT_VIEW, phone, baseTime.plusMinutes(i * 10)));
        }

        when(eventRepository.findByUserIdWithRelations(eq(testUserId), any(Pageable.class)))
                .thenReturn(events);

        UserShoppingSignals signals = signalService.extractSignals(testUserId);

        // Should be capped at MAX_PRODUCT_REPETITIONS = 5
        assertEquals(5, signals.getTotalInteractions());
    }

    @Test
    @DisplayName("Returns empty signals on cold start when no interaction events exist")
    void testColdStartEmptySignals() {
        when(eventRepository.findByUserIdWithRelations(eq(testUserId), any(Pageable.class)))
                .thenReturn(List.of());

        UserShoppingSignals signals = signalService.extractSignals(testUserId);

        assertNotNull(signals);
        assertEquals(testUserId, signals.getUserId());
        assertEquals(0, signals.getTotalInteractions());
        assertEquals(0.0, signals.getCategoryAffinity("Smartphones"));
        assertEquals(0.0, signals.getBrandAffinity("Apple"));
    }

    private UserInteractionEventEntity createEvent(InteractionType type, ProductEntity product, LocalDateTime createdAt) {
        com.pricepilot.user.UserEntity user = new com.pricepilot.user.UserEntity();
        user.setId(testUserId);
        return UserInteractionEventEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .product(product)
                .interactionType(type)
                .createdAt(createdAt)
                .build();
    }
}
