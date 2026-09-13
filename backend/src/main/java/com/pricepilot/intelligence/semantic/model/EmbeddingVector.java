package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing a high-dimensional dense embedding vector.
 * Encapsulates core geometric operations including cosine similarity, dot product,
 * Euclidean distance, and L2 normalization.
 */
public final class EmbeddingVector implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final double EPSILON = 1e-12;

    private final float[] values;

    private EmbeddingVector(float[] values) {
        this.values = Objects.requireNonNull(values, "Vector values cannot be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("Vector dimension must be greater than zero");
        }
    }

    /**
     * Creates an EmbeddingVector from a primitive float array.
     * Defensive copy is made to maintain immutability.
     */
    public static EmbeddingVector of(float[] values) {
        Objects.requireNonNull(values, "Vector values cannot be null");
        return new EmbeddingVector(Arrays.copyOf(values, values.length));
    }

    /**
     * Creates an EmbeddingVector from a List of Floats.
     */
    public static EmbeddingVector of(List<Float> values) {
        Objects.requireNonNull(values, "Vector values list cannot be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Vector dimension must be greater than zero");
        }
        float[] arr = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Float val = values.get(i);
            arr[i] = (val != null) ? val : 0.0f;
        }
        return new EmbeddingVector(arr);
    }

    /**
     * Parses a string of space or comma-separated float numbers into an EmbeddingVector.
     */
    public static EmbeddingVector parse(String serialized) {
        Objects.requireNonNull(serialized, "Serialized vector string cannot be null");
        String trimmed = serialized.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Serialized vector string cannot be empty");
        }
        String[] tokens = trimmed.split("[,\\s]+");
        float[] arr = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            arr[i] = Float.parseFloat(tokens[i]);
        }
        return new EmbeddingVector(arr);
    }

    public int dimension() {
        return values.length;
    }

    public float get(int index) {
        if (index < 0 || index >= values.length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for dimension " + values.length);
        }
        return values[index];
    }

    /**
     * Returns a defensive copy of the raw float values.
     */
    public float[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * Calculates the dot product with another vector.
     */
    public double dotProduct(EmbeddingVector other) {
        checkDimensionality(other);
        double dot = 0.0;
        for (int i = 0; i < values.length; i++) {
            dot += (double) values[i] * (double) other.values[i];
        }
        return dot;
    }

    /**
     * Calculates the L2 norm (magnitude) of this vector.
     */
    public double magnitude() {
        double sumSq = 0.0;
        for (float v : values) {
            sumSq += (double) v * (double) v;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * Calculates the Cosine Similarity between this vector and another.
     * Range: [-1.0, 1.0].
     */
    public double cosineSimilarity(EmbeddingVector other) {
        checkDimensionality(other);
        double dot = dotProduct(other);
        double magA = magnitude();
        double magB = other.magnitude();

        if (magA < EPSILON || magB < EPSILON) {
            return 0.0;
        }
        double similarity = dot / (magA * magB);
        // Clamp to valid range [-1.0, 1.0] due to floating-point rounding
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    /**
     * Calculates the Euclidean distance between this vector and another.
     */
    public double euclideanDistance(EmbeddingVector other) {
        checkDimensionality(other);
        double sumSq = 0.0;
        for (int i = 0; i < values.length; i++) {
            double diff = (double) values[i] - (double) other.values[i];
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * Returns an L2 unit-normalized version of this vector.
     */
    public EmbeddingVector normalize() {
        double mag = magnitude();
        if (mag < EPSILON) {
            return this;
        }
        float[] normalized = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (float) ((double) values[i] / mag);
        }
        return new EmbeddingVector(normalized);
    }

    /**
     * Serializes the vector to a compact comma-separated string representation.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder(values.length * 10);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private void checkDimensionality(EmbeddingVector other) {
        Objects.requireNonNull(other, "Target vector cannot be null");
        if (this.values.length != other.values.length) {
            throw new IllegalArgumentException(
                    String.format("Vector dimension mismatch: %d vs %d", this.values.length, other.values.length)
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingVector that = (EmbeddingVector) o;
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "EmbeddingVector[dimension=" + values.length + "]";
    }
}
