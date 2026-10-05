package com.pricepilot.ai;

import com.pricepilot.ai.dto.AiExplainRequest;
import com.pricepilot.ai.dto.AiPredictRequest;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.common.HealthController;
import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import com.pricepilot.intelligence.assistant.dto.AssistantResponseDTO;
import com.pricepilot.intelligence.assistant.dto.CreateConversationRequest;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import com.pricepilot.intelligence.assistant.service.ShoppingAssistantService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.explanation.HybridAiExplanationGenerator;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pricepilot.ai.url=",
        "pricepilot.ai.api-key=",
        "pricepilot.ai.enabled=true"
})
@Transactional
class OptionalAiStartupAndFallbackRegressionTest {

    @Autowired
    private AiClient aiClient;

    @Autowired
    private HybridAiExplanationGenerator explanationGenerator;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Autowired
    private ShoppingAssistantService shoppingAssistantService;

    @Autowired
    private AiServiceHealthIndicator aiServiceHealthIndicator;

    @Autowired
    private HealthController healthController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Scenario 1: Application context boots successfully without PRICEPILOT_AI_URL or API key")
    void scenario1_shouldBootApplicationContextWithoutAiUrl() {
        assertThat(aiClient).isNotNull();
        assertThat(aiClient.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Scenario 1 Fail-Fast: When AI is unconfigured, AI client methods fail fast without network socket errors")
    void scenario1_shouldFailFastWhenAiIsUnconfigured() {
        AiPredictRequest predictRequest = AiPredictRequest.builder()
                .userId(UUID.randomUUID().toString())
                .algorithm("HYBRID")
                .limit(5)
                .candidates(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> aiClient.predict(predictRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pricepilot.ai.url is blank");

        AiExplainRequest explainRequest = new AiExplainRequest(
                UUID.randomUUID().toString(),
                "iPhone 15",
                "BEST_OVERALL",
                95.0,
                0.90,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThatThrownBy(() -> aiClient.explain(explainRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pricepilot.ai.url is blank");
    }

    @Test
    @DisplayName("Scenario 2: Core deterministic recommendation flow succeeds without external AI")
    void scenario2_shouldProduceDeterministicRecommendationsWithoutExternalAi() {
        // 2a. Test Explanation Generator deterministic fallback
        ProductResponseDTO productDto = ProductResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("MacBook Pro M3")
                .category("Laptops")
                .build();

        RecommendationExplanation explanation = explanationGenerator.generateExplanation(
                productDto,
                List.of(productDto),
                Collections.emptyList(),
                Collections.emptyList(),
                RecommendationType.BEST_OVERALL,
                92.5,
                0.88
        );

        assertThat(explanation).isNotNull();
        assertThat(explanation.productId()).isEqualTo(productDto.getId());
        assertThat(explanation.modelName()).isEqualTo("PricePilot-Deterministic-Rules");
        assertThat(explanation.summaryExplanation()).isNotBlank();
        assertThat(explanation.explanationStrategy()).isEqualTo("DETERMINISTIC_RULE_BASED");

        // 2b. Test AiGatewayService deterministic fallback to RuleBasedRecommendationEngine
        ProductEntity entity = ProductEntity.builder()
                .name("Dell XPS 15")
                .category("Laptops")
                .brand("Dell")
                .build();
        entity.setId(UUID.randomUUID());

        List<ScoredProduct> recommendations = aiGatewayService.recommend(
                UUID.randomUUID(),
                List.of(entity),
                RecommendationProfile.builder()
                        .preferredCategories(Map.of())
                        .preferredBrands(Map.of())
                        .preferredSellers(Map.of())
                        .build(),
                Collections.emptyList(),
                Collections.emptyList(),
                "HYBRID",
                5
        );

        assertThat(recommendations).isNotNull();
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).getAlgorithm()).isEqualTo("Rule-Based");
        assertThat(recommendations.get(0).getProduct().getName()).isEqualTo("Dell XPS 15");
    }

    @Test
    @DisplayName("Scenario 3: Shopping Assistant succeeds with local intent classification and authoritative catalogue discovery")
    void scenario3_shouldExecuteAssistantQueriesDeterministically() {
        UserEntity user = userRepository.save(UserEntity.builder()
                .email("test-assistant-" + UUID.randomUUID() + "@example.com")
                .password("hashedpassword123")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        var convDto = shoppingAssistantService.createConversation(
                user.getId(),
                new CreateConversationRequest("Laptops inquiry", "I want to find a laptop")
        );
        assertThat(convDto).isNotNull();
        assertThat(convDto.getId()).isNotNull();

        AssistantResponseDTO response = shoppingAssistantService.sendMessage(
                convDto.getId(),
                user.getId(),
                new SendMessageRequest("Find laptops", null)
        );

        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isEqualTo(AssistantIntent.DISCOVERY);
        assertThat(response.getResponse()).isNotBlank();
        assertThat(response.getEvidenceBundle()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 4: AI health indicator and application health endpoint remain operational")
    void scenario4_shouldReportOperationalHealthWithStandbyAi() {
        Health aiHealth = aiServiceHealthIndicator.health();
        assertThat(aiHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(aiHealth.getDetails()).containsKey("service");
        assertThat((String) aiHealth.getDetails().get("service")).contains("deterministic fallback active");

        ResponseEntity<Map<String, Object>> healthResponse = healthController.getHealth();
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthResponse.getBody()).isNotNull();
        assertThat(healthResponse.getBody().get("status")).isEqualTo("UP");
        assertThat(healthResponse.getBody().get("database")).isEqualTo("UP");
        assertThat(healthResponse.getBody().get("ai_service")).isEqualTo("STANDBY_DETERMINISTIC");
    }
}
