package com.pricepilot.intelligence.comparison.dto;

import java.util.Collections;
import java.util.List;
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
    private List<UUID> highlightedProductIds;
    private String rowType;

    public ComparisonRow() {
        this.highlightedProductIds = Collections.emptyList();
    }

    public ComparisonRow(String featureName, String category, Map<UUID, String> valuesByProductId, boolean isHighlight) {
        this.featureName = featureName;
        this.category = category;
        this.valuesByProductId = valuesByProductId;
        this.isHighlight = isHighlight;
        this.highlightedProductIds = Collections.emptyList();
        this.rowType = "GENERAL";
    }

    public ComparisonRow(String featureName, String category, Map<UUID, String> valuesByProductId, boolean isHighlight, List<UUID> highlightedProductIds, String rowType) {
        this.featureName = featureName;
        this.category = category;
        this.valuesByProductId = valuesByProductId;
        this.isHighlight = isHighlight;
        this.highlightedProductIds = highlightedProductIds != null ? highlightedProductIds : Collections.emptyList();
        this.rowType = rowType != null ? rowType : "GENERAL";
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

    public List<UUID> getHighlightedProductIds() {
        return highlightedProductIds;
    }

    public void setHighlightedProductIds(List<UUID> highlightedProductIds) {
        this.highlightedProductIds = highlightedProductIds;
    }

    public String getRowType() {
        return rowType;
    }

    public void setRowType(String rowType) {
        this.rowType = rowType;
    }
}
