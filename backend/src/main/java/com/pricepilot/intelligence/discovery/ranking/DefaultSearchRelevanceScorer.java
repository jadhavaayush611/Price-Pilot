package com.pricepilot.intelligence.discovery.ranking;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Default deterministic search relevance scorer.
 * Computes transparent, explainable relevance scores and applies deterministic tie-breaking.
 */
@Component
public class DefaultSearchRelevanceScorer implements SearchRelevanceStrategy {

    private final QueryNormalizer queryNormalizer;

    public static final double EXACT_NAME_MATCH_WEIGHT = 100.0;
    public static final double PREFIX_NAME_MATCH_WEIGHT = 60.0;
    public static final double TOKEN_NAME_MATCH_WEIGHT = 40.0;
    public static final double BRAND_MATCH_WEIGHT = 30.0;
    public static final double CATEGORY_MATCH_WEIGHT = 20.0;
    public static final double DESCRIPTION_MATCH_WEIGHT = 10.0;

    public static final double EXCELLENT_DEAL_BOOST = 15.0;
    public static final double GOOD_DEAL_BOOST = 10.0;
    public static final double BUY_NOW_BOOST = 10.0;
    public static final double HISTORICAL_LOW_BOOST = 15.0;

    public DefaultSearchRelevanceScorer(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = queryNormalizer;
    }

    @Override
    public void scoreCandidate(ScoredProductCandidate candidate, InterpretedQuery query) {
        if (candidate == null || candidate.getProduct() == null) {
            return;
        }

        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        List<String> badges = new ArrayList<>();

        String normProductName = queryNormalizer.normalize(candidate.getProduct().getName());
        String normBrand = queryNormalizer.normalize(candidate.getProduct().getBrand());
        String normCategory = queryNormalizer.normalize(candidate.getProduct().getCategory());
        String normDesc = queryNormalizer.normalize(candidate.getProduct().getDescription());

        String targetQuery = query.getCleanSearchTerms();
        if (targetQuery == null || targetQuery.trim().isEmpty()) {
            targetQuery = query.getNormalizedQuery() != null ? query.getNormalizedQuery() : "";
        }

        // 1. Exact Name Match
        if (!normProductName.isEmpty() && normProductName.equals(targetQuery)) {
            score += EXACT_NAME_MATCH_WEIGHT;
            reasons.add("Exact product-name match");
        } else if (!normProductName.isEmpty() && targetQuery.length() >= 3 && normProductName.startsWith(targetQuery)) {
            // 2. Prefix Match
            score += PREFIX_NAME_MATCH_WEIGHT;
            reasons.add("Product name starts with query terms");
        }

        // 3. Token Match against product name
        List<String> queryTokens = query.getSearchTokens();
        if (queryTokens != null && !queryTokens.isEmpty()) {
            long matchedTokens = queryTokens.stream()
                    .filter(t -> normProductName.contains(t))
                    .count();
            if (matchedTokens > 0) {
                double tokenRatio = (double) matchedTokens / queryTokens.size();
                double tokenPoints = tokenRatio * TOKEN_NAME_MATCH_WEIGHT;
                score += tokenPoints;
                if (matchedTokens == queryTokens.size() && !reasons.contains("Exact product-name match")) {
                    reasons.add("Matches all query terms in product name");
                } else if (!reasons.contains("Exact product-name match")) {
                    reasons.add("Matches " + matchedTokens + "/" + queryTokens.size() + " terms in product name");
                }
            }
        }

        // 4. Brand Match
        if (query.getDetectedBrand() != null && normBrand.equalsIgnoreCase(queryNormalizer.normalize(query.getDetectedBrand()))) {
            score += BRAND_MATCH_WEIGHT;
            reasons.add("Matches requested brand: " + query.getDetectedBrand());
        } else if (queryTokens != null && queryTokens.stream().anyMatch(t -> normBrand.contains(t))) {
            score += (BRAND_MATCH_WEIGHT * 0.7);
            reasons.add("Matches brand keyword: " + candidate.getProduct().getBrand());
        }

        // 5. Category Match
        if (query.getDetectedCategory() != null && normCategory.equalsIgnoreCase(queryNormalizer.normalize(query.getDetectedCategory()))) {
            score += CATEGORY_MATCH_WEIGHT;
            reasons.add("Matches requested category: " + query.getDetectedCategory());
        } else if (queryTokens != null && queryTokens.stream().anyMatch(t -> normCategory.contains(t))) {
            score += (CATEGORY_MATCH_WEIGHT * 0.7);
            reasons.add("Matches category keyword: " + candidate.getProduct().getCategory());
        }

        // 6. Description / Spec Match
        if (queryTokens != null && queryTokens.stream().anyMatch(t -> normDesc.contains(t))) {
            score += DESCRIPTION_MATCH_WEIGHT;
            reasons.add("Keywords found in product specifications/description");
        }

        // 7. Shopping Intelligence Signals (Phase 4 integration)
        if (candidate.getDealQuality() == DealQuality.EXCELLENT_DEAL) {
            score += EXCELLENT_DEAL_BOOST;
            badges.add("Best Value");
            reasons.add("Verified excellent deal based on price history");
        } else if (candidate.getDealQuality() == DealQuality.GOOD_DEAL) {
            score += GOOD_DEAL_BOOST;
            badges.add("Good Deal");
            reasons.add("Good deal relative to historical average");
        }

        if (candidate.getPurchaseSignal() == PurchaseSignal.BUY_NOW) {
            score += BUY_NOW_BOOST;
            badges.add("Buy Now");
            reasons.add("Strong buy recommendation based on price intelligence");
        }

        if (Boolean.TRUE.equals(candidate.getIsHistoricalLow())) {
            score += HISTORICAL_LOW_BOOST;
            badges.add("Lowest Historical Price");
            reasons.add("Currently at all-time historical low price");
        }

        if (candidate.getCurrentBestPrice() != null) {
            badges.add("In Stock");
        }

        if (candidate.getRating() != null && candidate.getRating() >= 4.5) {
            badges.add("Highly Rated");
            reasons.add("Highly rated by customers (" + candidate.getRating() + "★)");
        }

        candidate.setRelevanceScore(Math.round(score * 10.0) / 10.0);
        candidate.setRelevanceReasons(reasons);
        candidate.setBadges(badges);
    }

    @Override
    public Comparator<ScoredProductCandidate> getDeterministicComparator() {
        return (c1, c2) -> {
            // 1. Relevance score descending
            int comp = Double.compare(c2.getRelevanceScore(), c1.getRelevanceScore());
            if (comp != 0) return comp;

            // 2. Deal quality descending
            int dealRank1 = getDealQualityRank(c1.getDealQuality());
            int dealRank2 = getDealQualityRank(c2.getDealQuality());
            comp = Integer.compare(dealRank2, dealRank1);
            if (comp != 0) return comp;

            // 3. Product rating descending
            double rating1 = c1.getRating() != null ? c1.getRating() : 0.0;
            double rating2 = c2.getRating() != null ? c2.getRating() : 0.0;
            comp = Double.compare(rating2, rating1);
            if (comp != 0) return comp;

            // 4. Current price ascending (better value first)
            BigDecimal p1 = c1.getCurrentBestPrice() != null ? c1.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal p2 = c2.getCurrentBestPrice() != null ? c2.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            comp = p1.compareTo(p2);
            if (comp != 0) return comp;

            // 5. Product UUID lexicographical ascending (strictly deterministic)
            String id1 = c1.getProductId() != null ? c1.getProductId().toString() : "";
            String id2 = c2.getProductId() != null ? c2.getProductId().toString() : "";
            return id1.compareTo(id2);
        };
    }

    private int getDealQualityRank(DealQuality quality) {
        if (quality == null) return 0;
        return switch (quality) {
            case EXCELLENT_DEAL -> 5;
            case GOOD_DEAL -> 4;
            case FAIR_PRICE -> 3;
            case ABOVE_AVERAGE -> 2;
            case HIGH_PRICE -> 1;
            case INSUFFICIENT_DATA -> 0;
        };
    }
}
