package com.pricepilot.intelligence.semantic.contract;

import com.pricepilot.product.ProductEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;

/**
 * Architectural Deliverable: Canonical Product Text Contract (v1).
 *
 * Deterministically constructs standardized semantic text representations of Product entities
 * for ingestion into the PricePilot vector embedding pipeline.
 *
 * Explicit Field Inclusions (in exact deterministic order):
 * 1. title: <normalized_name>
 * 2. brand: <normalized_brand> (omitted if null/blank)
 * 3. category: <normalized_category>
 * 4. description: <normalized_description> (omitted if null/blank)
 *
 * Explicit Field Exclusions (Volatile / Non-Semantic):
 * - currentPrice, originalPrice, discountPercentage (Volatile price points)
 * - productUrl, imageUrl (URLs and media assets)
 * - seller, sellerId (Merchant-specific data)
 * - analytics (views, saves, watchlists, price changes)
 * - priceHistories (Time-series pricing events)
 * - searchVector (PostgreSQL FTS internal column)
 */
@Component
public class CanonicalProductTextBuilder {

    public static final String CANONICAL_VERSION = "product-semantic-v1";
    public static final String FIELD_DELIMITER = " | ";
    public static final int DEFAULT_MAX_DESCRIPTION_LENGTH = 2048;

    private final int maxDescriptionLength;

    public CanonicalProductTextBuilder() {
        this(DEFAULT_MAX_DESCRIPTION_LENGTH);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CanonicalProductTextBuilder(com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties properties) {
        this(properties != null && properties.getMaxDescriptionLength() > 0 ? properties.getMaxDescriptionLength() : DEFAULT_MAX_DESCRIPTION_LENGTH);
    }

    public CanonicalProductTextBuilder(int maxDescriptionLength) {
        this.maxDescriptionLength = maxDescriptionLength > 0 ? maxDescriptionLength : DEFAULT_MAX_DESCRIPTION_LENGTH;
    }

    /**
     * Builds a CanonicalProductText contract representation from a ProductEntity.
     */
    public CanonicalProductText build(ProductEntity product) {
        Objects.requireNonNull(product, "ProductEntity cannot be null");
        return build(product.getName(), product.getBrand(), product.getCategory(), product.getDescription());
    }

    /**
     * Builds a CanonicalProductText contract representation from raw product fields.
     */
    public CanonicalProductText build(String name, String brand, String category, String description) {
        String normName = normalizeRequiredField("name", name);
        String normCategory = normalizeRequiredField("category", category);
        String normBrand = normalizeOptionalField(brand);
        String normDescription = normalizeOptionalField(description);

        if (normDescription != null && normDescription.length() > maxDescriptionLength) {
            normDescription = normDescription.substring(0, maxDescriptionLength).trim();
        }

        StringBuilder sb = new StringBuilder(256);

        // 1. Title / Name (Required)
        sb.append("title: ").append(normName);

        // 2. Brand (Optional)
        if (normBrand != null && !normBrand.isEmpty()) {
            sb.append(FIELD_DELIMITER).append("brand: ").append(normBrand);
        }

        // 3. Category (Required)
        sb.append(FIELD_DELIMITER).append("category: ").append(normCategory);

        // 4. Description (Optional)
        if (normDescription != null && !normDescription.isEmpty()) {
            sb.append(FIELD_DELIMITER).append("description: ").append(normDescription);
        }

        String canonicalString = sb.toString();
        String semanticHash = computeSha256(canonicalString);

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("canonicalVersion", CANONICAL_VERSION);
        attributes.put("semanticHash", semanticHash);
        attributes.put("category", normCategory);
        if (normBrand != null && !normBrand.isEmpty()) {
            attributes.put("brand", normBrand);
        }

        return new CanonicalProductText(canonicalString, CANONICAL_VERSION, semanticHash, attributes);
    }

    /**
     * Detects if there is a semantic difference between two product field states.
     * Returns true if any semantic field (name, brand, category, description) has changed.
     */
    public boolean hasSemanticDifference(
            String name1, String brand1, String category1, String desc1,
            String name2, String brand2, String category2, String desc2) {
        CanonicalProductText t1 = build(name1, brand1, category1, desc1);
        CanonicalProductText t2 = build(name2, brand2, category2, desc2);
        return !t1.getSemanticHash().equals(t2.getSemanticHash());
    }

    /**
     * Detects if there is a semantic difference between two ProductEntity instances.
     */
    public boolean hasSemanticDifference(ProductEntity p1, ProductEntity p2) {
        if (p1 == p2) return false;
        if (p1 == null || p2 == null) return true;
        return hasSemanticDifference(
                p1.getName(), p1.getBrand(), p1.getCategory(), p1.getDescription(),
                p2.getName(), p2.getBrand(), p2.getCategory(), p2.getDescription()
        );
    }

    public String getVersion() {
        return CANONICAL_VERSION;
    }

    /**
     * Normalizes required fields. Rejects null or blank values.
     */
    private String normalizeRequiredField(String fieldName, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Product " + fieldName + " is required and cannot be null");
        }
        String normalized = cleanText(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Product " + fieldName + " cannot be empty or whitespace");
        }
        return normalized;
    }

    /**
     * Normalizes optional fields. Returns null if input is null or blank after cleaning.
     */
    private String normalizeOptionalField(String value) {
        if (value == null) {
            return null;
        }
        String normalized = cleanText(value);
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Standard text cleaning:
     * 1. Unicode NFC normalization
     * 2. Strip non-printable control characters
     * 3. Collapse whitespace runs to single space
     * 4. Trim ends
     */
    private String cleanText(String input) {
        if (input == null) return "";
        // 1. Unicode NFC Canonical Composition
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        // 2. Remove control characters
        String noCtrl = nfc.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        // 3. Collapse multiple whitespace characters into a single space
        return noCtrl.replaceAll("\\s+", " ").trim();
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed in Java runtime
            return Integer.toHexString(text.hashCode());
        }
    }
}
