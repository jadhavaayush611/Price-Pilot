package com.pricepilot.intelligence.discovery.hybrid;

import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.product.ProductEntity;

import java.util.List;
import java.util.Map;

/**
 * Strategy contract for fusing structured search candidates and semantic search candidates.
 */
public interface HybridCandidateFusionStrategy {

    /**
     * Fuses structured and semantic candidate lists into a deduplicated, ranked list of HybridCandidates.
     *
     * @param structuredProducts Ordered list of products from structured retrieval
     * @param semanticResults Ordered list of vector similarity search results
     * @param productEntityMap Map of productId to ProductEntity for resolving semantic entities
     * @param structuredWeight Relative weight for structured rank contribution
     * @param semanticWeight Relative weight for semantic rank contribution
     * @param maxFusedLimit Maximum number of fused candidates to return
     * @return Deduplicated and ranked list of HybridCandidates
     */
    List<HybridCandidate> fuseCandidates(
            List<ProductEntity> structuredProducts,
            List<SimilaritySearchResult> semanticResults,
            Map<java.util.UUID, ProductEntity> productEntityMap,
            double structuredWeight,
            double semanticWeight,
            int maxFusedLimit
    );
}
