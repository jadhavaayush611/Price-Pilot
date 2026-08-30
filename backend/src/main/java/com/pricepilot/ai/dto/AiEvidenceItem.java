package com.pricepilot.ai.dto;

public class AiEvidenceItem {

    private String productId;
    private String productName;
    private String type;
    private String description;
    private String metricName;
    private Object metricValue;
    private Object comparisonValue;
    private boolean positive;
    private double importance;

    public AiEvidenceItem() {
    }

    public AiEvidenceItem(String productId, String productName, String type, String description,
                          String metricName, Object metricValue, Object comparisonValue,
                          boolean positive, double importance) {
        this.productId = productId;
        this.productName = productName;
        this.type = type;
        this.description = description;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.comparisonValue = comparisonValue;
        this.positive = positive;
        this.importance = importance;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Object getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Object metricValue) {
        this.metricValue = metricValue;
    }

    public Object getComparisonValue() {
        return comparisonValue;
    }

    public void setComparisonValue(Object comparisonValue) {
        this.comparisonValue = comparisonValue;
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }
}
