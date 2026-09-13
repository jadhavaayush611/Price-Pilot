package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderUnavailableException;
import com.pricepilot.intelligence.semantic.exception.InvalidEmbeddingInputException;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;
import com.pricepilot.intelligence.semantic.provider.LocalDeterministicEmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LocalDeterministicEmbeddingProviderTest {

    private LocalDeterministicEmbeddingProvider provider;
    private SemanticIntelligenceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SemanticIntelligenceProperties();
        properties.setModelName("local-hash-embedding");
        properties.setModelVersion("v1");
        properties.setDimension(64);
        properties.setMaxInputLength(4096);
        properties.setEnabled(true);

        provider = new LocalDeterministicEmbeddingProvider(properties);
    }

    @Test
    @DisplayName("Should generate single embedding with correct dimensionality and unit norm")
    void testEmbedSingle() {
        EmbeddingVector vector = provider.embed("Apple iPhone 15 Pro Max 256GB Natural Titanium");

        assertThat(vector).isNotNull();
        assertThat(vector.dimension()).isEqualTo(64);
        assertThat(vector.magnitude()).isEqualTo(1.0, within(1e-5));
    }

    @Test
    @DisplayName("Should be 100% deterministic across multiple repeated executions")
    void testDeterminism() {
        String text = "Sony WH-1000XM5 Wireless Noise-Canceling Headphones";
        EmbeddingVector first = provider.embed(text);

        for (int i = 0; i < 50; i++) {
            EmbeddingVector repeated = provider.embed(text);
            assertThat(repeated.getValues()).containsExactly(first.getValues());
            assertThat(first.cosineSimilarity(repeated)).isEqualTo(1.0, within(1e-6));
        }
    }

    @Test
    @DisplayName("Should generate batch embeddings preserving order and count")
    void testEmbedBatch() {
        List<String> inputs = List.of(
                "Apple iPhone 15 Pro",
                "Samsung Galaxy S24 Ultra",
                "Google Pixel 8 Pro"
        );

        List<EmbeddingVector> vectors = provider.embedBatch(inputs);

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0).dimension()).isEqualTo(64);
        assertThat(vectors.get(1).dimension()).isEqualTo(64);
        assertThat(vectors.get(2).dimension()).isEqualTo(64);

        // Verification that single embed matches batch embed
        assertThat(vectors.get(0)).isEqualTo(provider.embed(inputs.get(0)));
        assertThat(vectors.get(1)).isEqualTo(provider.embed(inputs.get(1)));
        assertThat(vectors.get(2)).isEqualTo(provider.embed(inputs.get(2)));
    }

    @Test
    @DisplayName("Should exhibit semantic sensitivity (similar phrases have higher similarity than unrelated phrases)")
    void testSemanticSensitivity() {
        EmbeddingVector iphone1 = provider.embed("Apple iPhone 15 Pro 128GB Black Titanium");
        EmbeddingVector iphone2 = provider.embed("Apple iPhone 15 Pro 256GB Blue Titanium");
        EmbeddingVector shoes = provider.embed("Men Air Zoom Pegasus Running Shoes Black");

        double similarityRelated = iphone1.cosineSimilarity(iphone2);
        double similarityUnrelated = iphone1.cosineSimilarity(shoes);

        assertThat(similarityRelated).isGreaterThan(0.50);
        assertThat(similarityUnrelated).isLessThan(0.35);
        assertThat(similarityRelated).isGreaterThan(similarityUnrelated * 2.0);
    }

    @Test
    @DisplayName("Should reject null, empty, or whitespace-only inputs")
    void testInvalidInputs() {
        assertThatThrownBy(() -> provider.embed(null))
                .isInstanceOf(InvalidEmbeddingInputException.class)
                .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> provider.embed(""))
                .isInstanceOf(InvalidEmbeddingInputException.class)
                .hasMessageContaining("cannot be empty or blank");

        assertThatThrownBy(() -> provider.embed("   \n\t  "))
                .isInstanceOf(InvalidEmbeddingInputException.class)
                .hasMessageContaining("cannot be empty or blank");

        assertThatThrownBy(() -> provider.embedBatch(null))
                .isInstanceOf(InvalidEmbeddingInputException.class);
    }

    @Test
    @DisplayName("Should safely sanitize control characters and handle oversized inputs")
    void testInputSanitization() {
        String longText = "Product " + "a".repeat(10000);
        EmbeddingVector vector = provider.embed(longText);
        assertThat(vector).isNotNull();
        assertThat(vector.dimension()).isEqualTo(64);

        String textWithCtrl = "Apple iPhone\u0000\u0007\u001F Pro Max";
        EmbeddingVector vectorClean = provider.embed(textWithCtrl);
        assertThat(vectorClean).isNotNull();
    }

    @Test
    @DisplayName("Should throw EmbeddingProviderUnavailableException when marked unavailable")
    void testUnavailableProvider() {
        provider.setAvailable(false);
        assertThat(provider.isAvailable()).isFalse();

        assertThatThrownBy(() -> provider.embed("Apple iPhone"))
                .isInstanceOf(EmbeddingProviderUnavailableException.class)
                .hasMessageContaining("is currently unavailable");

        assertThatThrownBy(() -> provider.embedBatch(List.of("Apple iPhone")))
                .isInstanceOf(EmbeddingProviderUnavailableException.class);

        ProviderHealth health = provider.checkHealth();
        assertThat(health.isHealthy()).isFalse();
        assertThat(health.getMessage()).contains("unavailable");
    }

    @Test
    @DisplayName("Should return healthy status when provider is operational")
    void testHealthCheck() {
        ProviderHealth health = provider.checkHealth();
        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getModelName()).isEqualTo("local-hash-embedding");
        assertThat(health.getModelVersion()).isEqualTo("v1");
        assertThat(health.getDimension()).isEqualTo(64);
    }
}
