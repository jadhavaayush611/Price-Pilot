package com.pricepilot.ai.dto;

import java.util.List;

public class AiExplainRequest {

    private String recommendedProductId;
    private String recommendedProductName;
    private String recommendationType;
    private double score;
    private double confidence;
    private List<AiEvidenceItem> evidence;
    private List<AiEvidenceItem> tradeOffEvidence;

    public AiExplainRequest() {
    }

    public AiExplainRequest(String recommendedProductId, String recommendedProductName, String recommendationType,
                            double score, double confidence, List<AiEvidenceItem> evidence,
                            List<AiEvidenceItem> tradeOffEvidence) {
        this.recommendedProductId = recommendedProductId;
        this.recommendedProductName = recommendedProductName;
        this.recommendationType = recommendationType;
        this.score = score;
        this.confidence = confidence;
        this.evidence = evidence;
        this.tradeOffEvidence = tradeOffEvidence;
    }

    public String getRecommendedProductId() {
        return recommendedProductId;
    }

    public void setRecommendedProductId(String recommendedProductId) {
        this.recommendedProductId = recommendedProductId;
    }

    public String getRecommendedProductName() {
        return recommendedProductName;
    }

    public void setRecommendedProductName(String recommendedProductName) {
        this.recommendedProductName = recommendedProductName;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = recommendationType;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<AiEvidenceItem> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<AiEvidenceItem> evidence) {
        this.evidence = evidence;
    }

    public List<AiEvidenceItem> getTradeOffEvidence() {
        return tradeOffEvidence;
    }

    public void setTradeOffEvidence(List<AiEvidenceItem> tradeOffEvidence) {
        this.tradeOffEvidence = tradeOffEvidence;
    }
}
