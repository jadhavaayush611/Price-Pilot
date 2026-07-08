package com.pricepilot.recommendation.dto;

import com.pricepilot.product.ProductEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScoredProduct {
    private final ProductEntity product;
    private final double score;
    private List<String> reasons;

    public ScoredProduct(ProductEntity product, double score) {
        this.product = product;
        this.score = score;
    }

    public ScoredProduct(ProductEntity product, double score, List<String> reasons) {
        this.product = product;
        this.score = score;
        this.reasons = reasons;
    }
}
