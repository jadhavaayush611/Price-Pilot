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
public class ScoredRecommendation {
    private String productId;
    private double score;
    private List<String> reasons;
}
