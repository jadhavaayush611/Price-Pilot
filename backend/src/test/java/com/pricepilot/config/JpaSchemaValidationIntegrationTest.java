package com.pricepilot.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JpaSchemaValidationIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Should initialize EntityManagerFactory with all 19 JPA entities successfully")
    void shouldInitializeCompleteJpaSchema() {
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        // Verify all 19 managed entities are mapped in the metamodel
        var metamodel = entityManagerFactory.getMetamodel();
        var entityNames = metamodel.getEntities().stream()
                .map(e -> e.getJavaType().getSimpleName())
                .toList();

        assertThat(entityNames).contains(
                "ProductEntity",
                "SellerEntity",
                "ProductPriceEntity",
                "UserEntity",
                "SavedProductEntity",
                "PriceHistoryEntity",
                "ProductAnalyticsEntity",
                "UserInteractionEventEntity",
                "PriceWatchlistEntity",
                "ComparisonSessionEntity",
                "SavedComparisonEntity",
                "RecommendationMetadataEntity",
                "RecommendationHistoryEventEntity",
                "WatchlistAlertPreferenceEntity",
                "PriceAlertEntity",
                "UserShoppingPreferenceEntity",
                "AssistantConversationEntity",
                "AssistantMessageEntity",
                "SemanticEmbeddingEntity"
        );
    }
}
