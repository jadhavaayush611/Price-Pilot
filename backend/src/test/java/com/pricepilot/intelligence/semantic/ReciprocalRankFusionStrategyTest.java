package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.discovery.hybrid.CandidateProvenance;
import com.pricepilot.intelligence.discovery.hybrid.HybridCandidate;
import com.pricepilot.intelligence.discovery.hybrid.ReciprocalRankFusionStrategy;
import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.product.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ReciprocalRankFusionStrategyTest {

    private ReciprocalRankFusionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ReciprocalRankFusionStrategy(60.0);
    }

    @Test
    @DisplayName("Should correctly fuse structured-only, semantic-only, and dual candidates with dual-channel boost")
    void testFusionScoringAndProvenance() {
        UUID pBothId = UUID.randomUUID();
        UUID pStructOnlyId = UUID.randomUUID();
        UUID pSemOnlyId = UUID.randomUUID();

        ProductEntity pBoth = ProductEntity.builder().name("Both Product").build();
        pBoth.setId(pBothId);
        ProductEntity pStruct = ProductEntity.builder().name("Structured Only").build();
        pStruct.setId(pStructOnlyId);
        ProductEntity pSem = ProductEntity.builder().name("Semantic Only").build();
        pSem.setId(pSemOnlyId);

        Map<UUID, ProductEntity> entityMap = Map.of(
                pBothId, pBoth,
                pStructOnlyId, pStruct,
                pSemOnlyId, pSem
        );

        // Structured: pBoth (rank 1), pStruct (rank 2)
        List<ProductEntity> structured = List.of(pBoth, pStruct);

        // Semantic: pBoth (rank 1, score 0.95), pSem (rank 2, score 0.85)
        EmbeddingMetadata meta = new EmbeddingMetadata("PRODUCT", "id", "m", "v1", 64, Map.of(), Instant.now(), Instant.now());
        List<SimilaritySearchResult> semantic = List.of(
                new SimilaritySearchResult("PRODUCT", pBothId.toString(), 0.95, meta, null),
                new SimilaritySearchResult("PRODUCT", pSemOnlyId.toString(), 0.85, meta, null)
        );

        List<HybridCandidate> fused = strategy.fuseCandidates(structured, semantic, entityMap, 0.70, 0.30, 10);

        assertThat(fused).hasSize(3);

        // Rank 1: pBoth (provenance BOTH, gets contributions from both channels)
        assertThat(fused.get(0).getProductId()).isEqualTo(pBothId);
        assertThat(fused.get(0).getProvenance()).isEqualTo(CandidateProvenance.BOTH);
        assertThat(fused.get(0).getStructuredRank()).isEqualTo(1);
        assertThat(fused.get(0).getSemanticRank()).isEqualTo(1);
        assertThat(fused.get(0).getSemanticSimilarityScore()).isEqualTo(0.95);

        // Verify other candidates
        HybridCandidate structCandidate = fused.stream().filter(c -> c.getProductId().equals(pStructOnlyId)).findFirst().orElseThrow();
        assertThat(structCandidate.getProvenance()).isEqualTo(CandidateProvenance.STRUCTURED);
        assertThat(structCandidate.getStructuredRank()).isEqualTo(2);
        assertThat(structCandidate.getSemanticRank()).isNull();

        HybridCandidate semCandidate = fused.stream().filter(c -> c.getProductId().equals(pSemOnlyId)).findFirst().orElseThrow();
        assertThat(semCandidate.getProvenance()).isEqualTo(CandidateProvenance.SEMANTIC);
        assertThat(semCandidate.getSemanticRank()).isEqualTo(2);
        assertThat(semCandidate.getStructuredRank()).isNull();
        assertThat(semCandidate.getSemanticSimilarityScore()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Should truncate fused candidate set to maxFusedLimit")
    void testMaxFusedLimitTruncation() {
        Map<UUID, ProductEntity> entityMap = new HashMap<>();
        List<ProductEntity> structured = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            ProductEntity p = ProductEntity.builder().name("Item " + i).build();
            p.setId(id);
            entityMap.put(id, p);
            structured.add(p);
        }

        List<HybridCandidate> fused = strategy.fuseCandidates(structured, Collections.emptyList(), entityMap, 0.7, 0.3, 5);

        assertThat(fused).hasSize(5);
    }
}
