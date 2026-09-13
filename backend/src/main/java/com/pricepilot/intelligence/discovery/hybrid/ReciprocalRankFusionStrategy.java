package com.pricepilot.intelligence.discovery.hybrid;

import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.product.ProductEntity;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Standard Reciprocal Rank Fusion (RRF) implementation for hybrid candidate ranking.
 *
 * Computes fused candidate score:
 * Score(d) = [ w_struct / (k + rank_struct(d)) ] + [ w_semantic / (k + rank_semantic(d)) ]
 *
 * Features:
 * - Robust against scale disparities between lexical keyword ranks and embedding cosine scores.
 * - Synergistic boost for items discovered by BOTH channels.
 * - Strictly deterministic tie-breaking by entity UUID.
 */
@Component
public class ReciprocalRankFusionStrategy implements HybridCandidateFusionStrategy {

    public static final double DEFAULT_RRF_K = 60.0;
    private final double rrfK;

    public ReciprocalRankFusionStrategy() {
        this(DEFAULT_RRF_K);
    }

    public ReciprocalRankFusionStrategy(double rrfK) {
        this.rrfK = rrfK > 0 ? rrfK : DEFAULT_RRF_K;
    }

    @Override
    public List<HybridCandidate> fuseCandidates(
            List<ProductEntity> structuredProducts,
            List<SimilaritySearchResult> semanticResults,
            Map<UUID, ProductEntity> productEntityMap,
            double structuredWeight,
            double semanticWeight,
            int maxFusedLimit) {

        int limit = maxFusedLimit > 0 ? maxFusedLimit : 100;
        double wStruct = structuredWeight > 0 ? structuredWeight : 0.70;
        double wSemantic = semanticWeight > 0 ? semanticWeight : 0.30;

        Map<UUID, Integer> structuredRanks = new LinkedHashMap<>();
        if (structuredProducts != null) {
            for (int i = 0; i < structuredProducts.size(); i++) {
                ProductEntity p = structuredProducts.get(i);
                if (p != null && p.getId() != null) {
                    structuredRanks.putIfAbsent(p.getId(), i + 1);
                }
            }
        }

        Map<UUID, Integer> semanticRanks = new LinkedHashMap<>();
        Map<UUID, Double> semanticScores = new LinkedHashMap<>();
        if (semanticResults != null) {
            for (int i = 0; i < semanticResults.size(); i++) {
                SimilaritySearchResult r = semanticResults.get(i);
                if (r != null && r.getEntityId() != null) {
                    try {
                        UUID id = UUID.fromString(r.getEntityId());
                        semanticRanks.putIfAbsent(id, i + 1);
                        semanticScores.putIfAbsent(id, r.getScore());
                    } catch (IllegalArgumentException ignored) {
                        // Skip non-UUID entity IDs
                    }
                }
            }
        }

        Set<UUID> allCandidateIds = new LinkedHashSet<>();
        allCandidateIds.addAll(structuredRanks.keySet());
        allCandidateIds.addAll(semanticRanks.keySet());

        List<HybridCandidate> fusedCandidates = new ArrayList<>(allCandidateIds.size());

        for (UUID id : allCandidateIds) {
            ProductEntity entity = productEntityMap.get(id);
            if (entity == null) {
                continue;
            }

            Integer structRank = structuredRanks.get(id);
            Integer semRank = semanticRanks.get(id);
            Double semScore = semanticScores.get(id);

            CandidateProvenance provenance;
            double score = 0.0;

            if (structRank != null && semRank != null) {
                provenance = CandidateProvenance.BOTH;
                score = (wStruct / (rrfK + structRank)) + (wSemantic / (rrfK + semRank));
            } else if (structRank != null) {
                provenance = CandidateProvenance.STRUCTURED;
                score = wStruct / (rrfK + structRank);
            } else {
                provenance = CandidateProvenance.SEMANTIC;
                score = wSemantic / (rrfK + semRank);
            }

            fusedCandidates.add(new HybridCandidate(
                    entity,
                    provenance,
                    structRank,
                    semRank,
                    semScore,
                    score
            ));
        }

        // Sort descending by fused score, tie-break by UUID ascending
        Collections.sort(fusedCandidates);

        if (fusedCandidates.size() > limit) {
            return new ArrayList<>(fusedCandidates.subList(0, limit));
        }
        return fusedCandidates;
    }
}
