package com.pricepilot.intelligence.comparison.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object representing a single feature comparison row across products.
 */
public class ComparisonRow {

    private String featureName;
    private String category;
    private Map<UUID, String> valuesByProductId;
    private boolean isHighlight;

    public ComparisonRow() {
    }

    public ComparisonRow(String featureName, String category, Map<UUID, String> valuesByProductId, boolean isHighlight) {
        this.featureName = featureName;
        this.category = category;
        this.valuesByProductId = valuesByProductId;
        this.isHighlight = isHighlight;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<UUID, String> getValuesByProductId() {
        return valuesByProductId;
    }

    public void setValuesByProductId(Map<UUID, String> valuesByProductId) {
        this.valuesByProductId = valuesByProductId;
    }

    public boolean isHighlight() {
        return isHighlight;
    }

    public void setHighlight(boolean highlight) {
        isHighlight = highlight;
    }
}
