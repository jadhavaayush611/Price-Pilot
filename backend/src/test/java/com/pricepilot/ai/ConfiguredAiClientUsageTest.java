package com.pricepilot.ai;

import com.pricepilot.ai.dto.AiExplainRequest;
import com.pricepilot.ai.dto.AiExplainResponse;
import com.pricepilot.ai.dto.AiPredictRequest;
import com.pricepilot.ai.dto.AiPredictResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pricepilot.ai.url=https://ai.example.com",
        "pricepilot.ai.api-key=test-secret-key"
})
class ConfiguredAiClientUsageTest {

    @Autowired
    private AiClient aiClient;

    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mockRestTemplate = mock(RestTemplate.class);
        Field restTemplateField = AiClientImpl.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(aiClient, mockRestTemplate);
    }

    @Test
    @DisplayName("Scenario 5 Property Binding: Binds configured URL and API key correctly")
    void scenario5_shouldBindProperties() throws Exception {
        assertThat(aiClient).isNotNull();

        Field urlField = AiClientImpl.class.getDeclaredField("aiUrl");
        urlField.setAccessible(true);
        String urlValue = (String) urlField.get(aiClient);

        Field keyField = AiClientImpl.class.getDeclaredField("apiKey");
        keyField.setAccessible(true);
        String keyValue = (String) keyField.get(aiClient);

        assertThat(urlValue).isEqualTo("https://ai.example.com");
        assertThat(keyValue).isEqualTo("test-secret-key");
    }

    @Test
    @DisplayName("Scenario 5 Health: Invokes configured /health endpoint")
    void scenario5_shouldTargetConfiguredHealth() {
        when(mockRestTemplate.getForObject(eq("https://ai.example.com/health"), eq(Map.class)))
                .thenReturn(Map.of("status", "UP"));

        boolean available = aiClient.isAvailable();
        assertThat(available).isTrue();
        verify(mockRestTemplate).getForObject(eq("https://ai.example.com/health"), eq(Map.class));
    }

    @Test
    @DisplayName("Scenario 5 Predict: Invokes configured /recommendations/predict endpoint")
    @SuppressWarnings("unchecked")
    void scenario5_shouldTargetConfiguredPredict() {
        AiPredictResponse mockResponse = AiPredictResponse.builder()
                .algorithm("HYBRID")
                .score(90.0)
                .recommendations(Collections.emptyList())
                .build();

        when(mockRestTemplate.postForObject(
                eq("https://ai.example.com/recommendations/predict"),
                any(HttpEntity.class),
                eq(AiPredictResponse.class)
        )).thenReturn(mockResponse);

        AiPredictRequest predictReq = AiPredictRequest.builder()
                .userId(UUID.randomUUID().toString())
                .algorithm("HYBRID")
                .limit(5)
                .candidates(Collections.emptyList())
                .build();

        AiPredictResponse predictResp = aiClient.predict(predictReq);
        assertThat(predictResp).isNotNull();
        assertThat(predictResp.getAlgorithm()).isEqualTo("HYBRID");

        ArgumentCaptor<HttpEntity<AiPredictRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(mockRestTemplate).postForObject(
                eq("https://ai.example.com/recommendations/predict"),
                captor.capture(),
                eq(AiPredictResponse.class)
        );

        assertThat(captor.getValue().getBody()).isEqualTo(predictReq);
    }

    @Test
    @DisplayName("Scenario 5 Explain: Invokes configured /recommendations/explain endpoint")
    @SuppressWarnings("unchecked")
    void scenario5_shouldTargetConfiguredExplain() {
        AiExplainResponse mockResponse = new AiExplainResponse(
                "Grounded AI explanation",
                Collections.emptyList(),
                Collections.emptyList(),
                "fastapi-llm-v1"
        );

        when(mockRestTemplate.postForObject(
                eq("https://ai.example.com/recommendations/explain"),
                any(HttpEntity.class),
                eq(AiExplainResponse.class)
        )).thenReturn(mockResponse);

        AiExplainRequest explainReq = new AiExplainRequest(
                UUID.randomUUID().toString(),
                "iPhone 15",
                "BEST_OVERALL",
                95.0,
                0.90,
                Collections.emptyList(),
                Collections.emptyList()
        );

        AiExplainResponse explainResp = aiClient.explain(explainReq);
        assertThat(explainResp).isNotNull();
        assertThat(explainResp.getExplanation()).isEqualTo("Grounded AI explanation");

        ArgumentCaptor<HttpEntity<AiExplainRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(mockRestTemplate).postForObject(
                eq("https://ai.example.com/recommendations/explain"),
                captor.capture(),
                eq(AiExplainResponse.class)
        );

        assertThat(captor.getValue().getBody()).isEqualTo(explainReq);
    }
}
