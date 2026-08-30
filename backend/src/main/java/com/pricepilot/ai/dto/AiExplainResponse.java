package com.pricepilot.ai.dto;

import java.util.List;

public class AiExplainResponse {

    private String explanation;
    private List<String> supportingFactors;
    private List<String> tradeOffs;
    private String model;

    public AiExplainResponse() {
    }

    public AiExplainResponse(String explanation, List<String> supportingFactors, List<String> tradeOffs, String model) {
        this.explanation = explanation;
        this.supportingFactors = supportingFactors;
        this.tradeOffs = tradeOffs;
        this.model = model;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getSupportingFactors() {
        return supportingFactors;
    }

    public void setSupportingFactors(List<String> supportingFactors) {
        this.supportingFactors = supportingFactors;
    }

    public List<String> getTradeOffs() {
        return tradeOffs;
    }

    public void setTradeOffs(List<String> tradeOffs) {
        this.tradeOffs = tradeOffs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
