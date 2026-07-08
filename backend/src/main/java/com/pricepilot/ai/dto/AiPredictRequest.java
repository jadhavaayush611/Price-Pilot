package com.pricepilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPredictRequest {
    private String userId;
    private String algorithm;
    private int limit;
    private List<ProductFeature> candidates;
    private UserProfile userProfile;
    private List<UserInteraction> interactions;
}
