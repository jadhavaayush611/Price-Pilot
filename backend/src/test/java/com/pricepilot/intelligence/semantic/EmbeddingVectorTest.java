package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EmbeddingVectorTest {

    @Test
    @DisplayName("Should create EmbeddingVector and access values defensively")
    void testCreateAndDefensiveCopy() {
        float[] raw = new float[]{1.0f, 2.0f, 3.0f};
        EmbeddingVector vector = EmbeddingVector.of(raw);

        assertThat(vector.dimension()).isEqualTo(3);
        assertThat(vector.get(0)).isEqualTo(1.0f);
        assertThat(vector.get(1)).isEqualTo(2.0f);
        assertThat(vector.get(2)).isEqualTo(3.0f);

        // Modifying source array should not alter internal state
        raw[0] = 999.0f;
        assertThat(vector.get(0)).isEqualTo(1.0f);

        // Modifying returned array should not alter internal state
        float[] copy = vector.getValues();
        copy[0] = 888.0f;
        assertThat(vector.get(0)).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("Should create EmbeddingVector from List of Floats")
    void testCreateFromList() {
        EmbeddingVector vector = EmbeddingVector.of(List.of(0.5f, 1.5f, -0.5f));
        assertThat(vector.dimension()).isEqualTo(3);
        assertThat(vector.get(0)).isEqualTo(0.5f);
        assertThat(vector.get(1)).isEqualTo(1.5f);
        assertThat(vector.get(2)).isEqualTo(-0.5f);
    }

    @Test
    @DisplayName("Should reject empty or null values")
    void testRejectEmptyOrNull() {
        assertThatThrownBy(() -> EmbeddingVector.of((float[]) null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> EmbeddingVector.of(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EmbeddingVector.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should calculate dot product accurately")
    void testDotProduct() {
        EmbeddingVector v1 = EmbeddingVector.of(new float[]{1.0f, 2.0f, 3.0f});
        EmbeddingVector v2 = EmbeddingVector.of(new float[]{4.0f, -5.0f, 6.0f});

        // 1*4 + 2*(-5) + 3*6 = 4 - 10 + 18 = 12
        assertThat(v1.dotProduct(v2)).isEqualTo(12.0, within(1e-6));
    }

    @Test
    @DisplayName("Should calculate cosine similarity accurately and clamp between -1 and 1")
    void testCosineSimilarity() {
        EmbeddingVector v1 = EmbeddingVector.of(new float[]{1.0f, 0.0f, 0.0f});
        EmbeddingVector v2 = EmbeddingVector.of(new float[]{1.0f, 0.0f, 0.0f});
        EmbeddingVector v3 = EmbeddingVector.of(new float[]{-1.0f, 0.0f, 0.0f});
        EmbeddingVector v4 = EmbeddingVector.of(new float[]{0.0f, 1.0f, 0.0f});

        assertThat(v1.cosineSimilarity(v2)).isEqualTo(1.0, within(1e-6));
        assertThat(v1.cosineSimilarity(v3)).isEqualTo(-1.0, within(1e-6));
        assertThat(v1.cosineSimilarity(v4)).isEqualTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("Should calculate Euclidean distance accurately")
    void testEuclideanDistance() {
        EmbeddingVector v1 = EmbeddingVector.of(new float[]{0.0f, 0.0f});
        EmbeddingVector v2 = EmbeddingVector.of(new float[]{3.0f, 4.0f});

        assertThat(v1.euclideanDistance(v2)).isEqualTo(5.0, within(1e-6));
    }

    @Test
    @DisplayName("Should normalize vector to unit magnitude")
    void testNormalize() {
        EmbeddingVector v = EmbeddingVector.of(new float[]{3.0f, 4.0f}).normalize();

        assertThat(v.magnitude()).isEqualTo(1.0, within(1e-6));
        assertThat(v.get(0)).isEqualTo(0.6f, within(1e-6f));
        assertThat(v.get(1)).isEqualTo(0.8f, within(1e-6f));
    }

    @Test
    @DisplayName("Should serialize to and parse from string")
    void testSerializeAndParse() {
        EmbeddingVector original = EmbeddingVector.of(new float[]{1.25f, -3.5f, 0.0f, 42.1f});
        String serialized = original.serialize();
        EmbeddingVector parsed = EmbeddingVector.parse(serialized);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.dimension()).isEqualTo(4);
        assertThat(parsed.get(0)).isEqualTo(1.25f);
        assertThat(parsed.get(1)).isEqualTo(-3.5f);
    }

    @Test
    @DisplayName("Should throw exception when calculating similarity with mismatched dimensions")
    void testDimensionMismatch() {
        EmbeddingVector v1 = EmbeddingVector.of(new float[]{1.0f, 2.0f});
        EmbeddingVector v2 = EmbeddingVector.of(new float[]{1.0f, 2.0f, 3.0f});

        assertThatThrownBy(() -> v1.cosineSimilarity(v2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vector dimension mismatch");

        assertThatThrownBy(() -> v1.dotProduct(v2))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> v1.euclideanDistance(v2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
