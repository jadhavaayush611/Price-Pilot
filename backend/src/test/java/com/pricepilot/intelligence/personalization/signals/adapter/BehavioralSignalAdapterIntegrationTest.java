package com.pricepilot.intelligence.personalization.signals.adapter;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationSource;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalServiceImpl;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BehavioralSignalAdapter Integration Tests with Behavioral Aggregation Engine")
class BehavioralSignalAdapterIntegrationTest {

    @Mock
    private UserInteractionEventRepository eventRepository;

    private BehavioralSignalServiceImpl behavioralSignalService;
    private BehavioralSignalAdapter adapter;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        behavioralSignalService = new BehavioralSignalServiceImpl(eventRepository);
        adapter = new BehavioralSignalAdapter(behavioralSignalService);
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Preserves existing deduplication and satiation rules through adapter")
    void testDeduplicationAndSatiationPreservedThroughAdapter() {
        UUID prodId = UUID.randomUUID();
        ProductEntity product = ProductEntity.builder()
                .name("MacBook Pro")
                .category("Laptops")
                .brand("Apple")
                .build();
        product.setId(prodId);

        LocalDateTime now = LocalDateTime.now();
        List<UserInteractionEventEntity> events = new ArrayList<>();

        com.pricepilot.user.UserEntity userEntity = com.pricepilot.user.UserEntity.builder().build();
        userEntity.setId(testUserId);

        // 10 repeated view events within deduplication window (< 180s)
        for (int i = 0; i < 10; i++) {
            UserInteractionEventEntity event = UserInteractionEventEntity.builder()
                    .user(userEntity)
                    .interactionType(InteractionType.PRODUCT_VIEW)
                    .product(product)
                    .createdAt(now.plusSeconds(i * 10)) // within 3 min deduplication window
                    .build();
            events.add(event);
        }

        // Aggregate through existing v1.1 engine
        UserShoppingSignals signals = behavioralSignalService.aggregateSignalsFromEvents(testUserId, events);

        // Deduplication in v1.1 suppresses rapid duplicates: only 1 interaction counted
        assertEquals(1, signals.getTotalInteractions());

        // Adapt into PersonalizationContext
        PersonalizationContext context = adapter.mapToContext(testUserId, signals);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertFalse(context.isEmpty());

        // Context reflects aggregated signals with correct bounds
        assertEquals(1.0, context.getCategoryAffinity("laptops"));
        assertEquals(1.0, context.getBrandAffinity("apple"));

        // All signals are BEHAVIORAL_SIGNAL
        context.getSignals().forEach(sig ->
                assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, sig.source())
        );
    }
}
