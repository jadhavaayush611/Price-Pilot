package com.pricepilot.intelligence.semantic.provider;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderUnavailableException;
import com.pricepilot.intelligence.semantic.exception.InvalidEmbeddingInputException;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance, locally runnable, deterministic embedding provider.
 * Uses subword n-gram feature hashing with signed projections and L2 normalization.
 * Requires NO API keys, external models, or network connectivity.
 * Guarantees 100% deterministic vectors across JVMs, CI, and operating systems.
 */
@Component
public class LocalDeterministicEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalDeterministicEmbeddingProvider.class);

    private final String modelName;
    private final String modelVersion;
    private final int dimension;
    private final int maxInputLength;
    private final AtomicBoolean available = new AtomicBoolean(true);

    @Autowired
    public LocalDeterministicEmbeddingProvider(SemanticIntelligenceProperties properties) {
        Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");
        this.modelName = properties.getModelName() != null ? properties.getModelName() : "local-hash-embedding";
        this.modelVersion = properties.getModelVersion() != null ? properties.getModelVersion() : "v1";
        this.dimension = properties.getDimension() > 0 ? properties.getDimension() : 64;
        this.maxInputLength = properties.getMaxInputLength() > 0 ? properties.getMaxInputLength() : 4096;
        this.available.set(properties.isEnabled());
    }

    public LocalDeterministicEmbeddingProvider(String modelName, String modelVersion, int dimension, int maxInputLength) {
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        this.dimension = dimension > 0 ? dimension : 64;
        this.maxInputLength = maxInputLength > 0 ? maxInputLength : 4096;
        this.available.set(true);
    }

    @Override
    public EmbeddingVector embed(String text) {
        ensureAvailable();
        String sanitized = sanitizeInput(text);
        float[] vector = computeVector(sanitized);
        return EmbeddingVector.of(vector).normalize();
    }

    @Override
    public List<EmbeddingVector> embedBatch(List<String> texts) {
        ensureAvailable();
        if (texts == null) {
            throw new InvalidEmbeddingInputException("Input batch list cannot be null");
        }
        List<EmbeddingVector> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    public ProviderHealth checkHealth() {
        if (!available.get()) {
            return ProviderHealth.down("LocalDeterministicEmbeddingProvider", "Provider is disabled or marked unavailable");
        }
        return ProviderHealth.up("LocalDeterministicEmbeddingProvider", modelName, modelVersion, dimension);
    }

    @Override
    public boolean isAvailable() {
        return available.get();
    }

    public void setAvailable(boolean isAvailable) {
        this.available.set(isAvailable);
    }

    private void ensureAvailable() {
        if (!available.get()) {
            throw new EmbeddingProviderUnavailableException(
                    "Local embedding provider '" + modelName + ":" + modelVersion + "' is currently unavailable"
            );
        }
    }

    private String sanitizeInput(String input) {
        if (input == null) {
            throw new InvalidEmbeddingInputException("Input text cannot be null");
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidEmbeddingInputException("Input text cannot be empty or blank");
        }
        // Truncate to maximum allowable length for safety
        if (trimmed.length() > maxInputLength) {
            trimmed = trimmed.substring(0, maxInputLength);
        }
        // Remove control characters except standard whitespace
        return trimmed.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
    }

    private float[] computeVector(String text) {
        float[] vector = new float[dimension];
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            // Fallback for non-alphanumeric text (e.g. symbols)
            normalized = text.trim();
        }

        String[] tokens = normalized.split("\\s+");

        // 1. Word Tokens Feature Hashing (weight = 2.0)
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isEmpty()) continue;
            hashAndAccumulate(vector, "w:" + token, 2.0f);

            // 2. Word Bigrams (weight = 1.5)
            if (i < tokens.length - 1 && !tokens[i + 1].isEmpty()) {
                hashAndAccumulate(vector, "bi:" + token + "_" + tokens[i + 1], 1.5f);
            }

            // 3. Subword character n-grams (3-grams and 4-grams, weight = 0.5)
            int tokenLen = token.length();
            if (tokenLen >= 3) {
                for (int j = 0; j <= tokenLen - 3; j++) {
                    hashAndAccumulate(vector, "c3:" + token.substring(j, j + 3), 0.5f);
                }
            }
            if (tokenLen >= 4) {
                for (int j = 0; j <= tokenLen - 4; j++) {
                    hashAndAccumulate(vector, "c4:" + token.substring(j, j + 4), 0.3f);
                }
            }
        }

        // 4. Global character 3-grams over normalized string (weight = 0.2)
        int normLen = normalized.length();
        for (int i = 0; i <= normLen - 3; i += 2) {
            hashAndAccumulate(vector, "g3:" + normalized.substring(i, i + 3), 0.2f);
        }

        return vector;
    }

    private void hashAndAccumulate(float[] vector, String feature, float weight) {
        long hash = hash64(feature);
        int index = (int) (Math.abs(hash % dimension));
        // Use higher 32 bits for sign hashing (-1 or +1)
        float sign = ((hash >>> 32) & 1L) == 0 ? 1.0f : -1.0f;
        vector[index] += sign * weight;
    }

    /**
     * 64-bit deterministic hash (Murmur3-inspired 64-bit mixing).
     */
    private static long hash64(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        long h = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        // Avalanche mixing
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
