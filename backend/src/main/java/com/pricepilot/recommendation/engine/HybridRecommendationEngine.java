package com.pricepilot.recommendation.engine;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.explainability.RecommendationExplainer;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("HybridRecommendationEngine")
public class HybridRecommendationEngine implements RecommendationEngine {

    private final PopularityRecommendationEngine popularityEngine;
    private final ContentBasedRecommendationEngine contentEngine;
    private final CollaborativeFilteringEngine collaborativeEngine;
    private final RecommendationExplainer explainer;

    @Value("${pricepilot.recommendation.hybrid.weights.popularity:0.20}")
    private double wPopularity;

    @Value("${pricepilot.recommendation.hybrid.weights.content:0.35}")
    private double wContent;

    @Value("${pricepilot.recommendation.hybrid.weights.collaborative:0.45}")
    private double wCollaborative;

    public HybridRecommendationEngine(
            PopularityRecommendationEngine popularityEngine,
            ContentBasedRecommendationEngine contentEngine,
            CollaborativeFilteringEngine collaborativeEngine,
            RecommendationExplainer explainer) {
        this.popularityEngine = popularityEngine;
        this.contentEngine = contentEngine;
        this.collaborativeEngine = collaborativeEngine;
        this.explainer = explainer;
    }

    @Override
    public List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            int limit) {

        // 1. Get raw scores from sub-engines
        List<ScoredProduct> popRecs = popularityEngine.recommend(userId, candidates, profile, saved, watchlists, candidates.size());
        List<ScoredProduct> contentRecs = contentEngine.recommend(userId, candidates, profile, saved, watchlists, candidates.size());
        List<ScoredProduct> collabRecs = collaborativeEngine.recommend(userId, candidates, profile, saved, watchlists, candidates.size());

        // 2. Map productId to normalized score
        Map<UUID, Double> popNorm = normalizeScores(popRecs);
        Map<UUID, Double> contentNorm = normalizeScores(contentRecs);
        Map<UUID, Double> collabNorm = normalizeScores(collabRecs);

        // 3. Compute weighted hybrid score
        return candidates.stream()
                .map(product -> {
                    double sPop = popNorm.getOrDefault(product.getId(), 0.0);
                    double sContent = contentNorm.getOrDefault(product.getId(), 0.0);
                    double sCollab = collabNorm.getOrDefault(product.getId(), 0.0);

                    double score = wPopularity * sPop + wContent * sContent + wCollaborative * sCollab;
                    List<String> reasons = explainer.explain(product, profile, saved, watchlists, getAlgorithmName());
                    return new ScoredProduct(product, score, reasons);
                })
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "Hybrid";
    }

    private Map<UUID, Double> normalizeScores(List<ScoredProduct> scoredList) {
        if (scoredList == null || scoredList.isEmpty()) {
            return Collections.emptyMap();
        }

        double min = scoredList.stream().mapToDouble(ScoredProduct::getScore).min().orElse(0.0);
        double max = scoredList.stream().mapToDouble(ScoredProduct::getScore).max().orElse(0.0);
        double diff = max - min;

        Map<UUID, Double> normalized = new HashMap<>();
        for (ScoredProduct sp : scoredList) {
            double score = diff == 0 ? 1.0 : (sp.getScore() - min) / diff;
            normalized.put(sp.getProduct().getId(), score);
        }
        return normalized;
    }
}
