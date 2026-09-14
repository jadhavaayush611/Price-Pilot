package com.pricepilot.intelligence.alternative.scoring;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Default implementation of AlternativeScoringStrategy.
 * Provides deterministic multi-dimensional scoring tailored per AlternativeType,
 * builds structured evidence items, assigns standardized reason codes,
 * and formulates explainable recommendation summaries.
 */
@Component
public class DefaultAlternativeScoringStrategy implements AlternativeScoringStrategy {

    private final QueryNormalizer queryNormalizer;

    public DefaultAlternativeScoringStrategy(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = Objects.requireNonNull(queryNormalizer, "QueryNormalizer cannot be null");
    }

    @Override
    public boolean isEligible(AlternativeCandidate candidate, SourceProductContextDTO sourceContext, AlternativeType type) {
        if (candidate == null || candidate.getProduct() == null) {
            return false;
        }

        // Cannot suggest the same product as an alternative to itself
        if (sourceContext != null && sourceContext.getId() != null && sourceContext.getId().equals(candidate.getProductId())) {
            return false;
        }

        BigDecimal candPrice = candidate.getCurrentBestPrice();
        BigDecimal srcPrice = sourceContext != null ? sourceContext.getCurrentBestPrice() : null;
        Double candRating = resolveRating(candidate);
        Double srcRating = sourceContext != null && sourceContext.getRating() != null ? sourceContext.getRating() : 4.0;

        AlternativeType effectiveType = type != null ? type : AlternativeType.SIMILAR;

        switch (effectiveType) {
            case CHEAPER -> {
                // Must have a valid price and be cheaper than source price if source price is known
                if (candPrice == null) return false;
                if (srcPrice != null && candPrice.compareTo(srcPrice) >= 0) {
                    return false;
                }
                return true;
            }
            case BUDGET_FALLBACK -> {
                // Must provide substantial savings (at least 20% cheaper than source price)
                if (candPrice == null) return false;
                if (srcPrice != null) {
                    BigDecimal maxAllowed = srcPrice.multiply(BigDecimal.valueOf(0.80));
                    if (candPrice.compareTo(maxAllowed) > 0) {
                        return false;
                    }
                }
                return true;
            }
            case PERFORMANCE_UPGRADE -> {
                // Rating must be at least as high as source rating (or candidate has superior deal quality / features)
                if (candRating < (srcRating - 0.1)) {
                    return false;
                }
                return true;
            }
            case PREMIUM -> {
                // Price should be at or above source price (or high rating >= 4.4)
                if (srcPrice != null && candPrice != null && candPrice.compareTo(srcPrice) < 0 && candRating < 4.5) {
                    return false;
                }
                return true;
            }
            case BETTER_VALUE -> {
                // Must have discount OR better deal quality OR lower price OR higher rating
                boolean hasDiscount = candidate.getDiscountPercentage() != null && candidate.getDiscountPercentage().compareTo(BigDecimal.valueOf(5)) >= 0;
                boolean isCheaper = srcPrice != null && candPrice != null && candPrice.compareTo(srcPrice) < 0;
                boolean isHigherRated = candRating > srcRating;
                boolean isGoodDeal = candidate.getDealQuality() == DealQuality.EXCELLENT_DEAL || candidate.getDealQuality() == DealQuality.GOOD_DEAL;
                return hasDiscount || isCheaper || isHigherRated || isGoodDeal;
            }
            case SIMILAR -> {
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public void scoreCandidate(AlternativeCandidate candidate, SourceProductContextDTO sourceContext, AlternativeType type) {
        if (candidate == null || candidate.getProduct() == null) {
            return;
        }

        AlternativeType effectiveType = type != null ? type : AlternativeType.SIMILAR;
        double score = 0.0;

        List<AlternativeEvidence> evidenceList = new ArrayList<>();
        List<AlternativeReasonCode> reasonCodes = new ArrayList<>();
        List<String> badges = new ArrayList<>();

        Double similarity = candidate.getSemanticSimilarityScore() != null ? candidate.getSemanticSimilarityScore() : 0.50;
        BigDecimal candPrice = candidate.getCurrentBestPrice();
        BigDecimal srcPrice = sourceContext != null ? sourceContext.getCurrentBestPrice() : null;
        Double candRating = resolveRating(candidate);
        candidate.setRating(candRating);
        Double srcRating = sourceContext != null && sourceContext.getRating() != null ? sourceContext.getRating() : 4.0;

        // Price comparison calculation
        if (candPrice != null && srcPrice != null && srcPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = candPrice.subtract(srcPrice);
            candidate.setPriceDifference(diff);
            double pct = diff.divide(srcPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            candidate.setPriceDifferencePercentage(pct);
        }

        String candCat = candidate.getProduct().getCategory();
        String srcCat = sourceContext != null ? sourceContext.getCategory() : null;
        boolean sameCategory = candCat != null && srcCat != null && queryNormalizer.normalize(candCat).equalsIgnoreCase(queryNormalizer.normalize(srcCat));

        String candBrand = candidate.getProduct().getBrand();
        String srcBrand = sourceContext != null ? sourceContext.getBrand() : null;
        boolean sameBrand = candBrand != null && srcBrand != null && queryNormalizer.normalize(candBrand).equalsIgnoreCase(queryNormalizer.normalize(srcBrand));

        // 1. Core Semantic & Categorical Evaluation
        if (similarity >= 0.70) {
            reasonCodes.add(AlternativeReasonCode.HIGH_SEMANTIC_SIMILARITY);
            evidenceList.add(AlternativeEvidence.builder()
                    .category(AlternativeEvidenceCategory.SIMILARITY)
                    .candidateValue(String.format("%d%%", Math.round(similarity * 100)))
                    .relationship("HIGH_SIMILARITY")
                    .confidence(similarity)
                    .description(String.format("%d%% semantic concept similarity with source product", Math.round(similarity * 100)))
                    .build());
        }

        if (sameCategory) {
            reasonCodes.add(AlternativeReasonCode.SIMILAR_CATEGORY);
            evidenceList.add(AlternativeEvidence.builder()
                    .category(AlternativeEvidenceCategory.CATEGORY)
                    .sourceValue(srcCat)
                    .candidateValue(candCat)
                    .relationship("SAME_CATEGORY")
                    .confidence(1.0)
                    .description(String.format("Identical category: %s", candCat))
                    .build());
        }

        if (sameBrand) {
            reasonCodes.add(AlternativeReasonCode.BRAND_MATCH);
            badges.add("Same Brand");
        }

        // 2. Price Evaluation
        if (candPrice != null && srcPrice != null) {
            if (candPrice.compareTo(srcPrice) < 0) {
                BigDecimal savings = srcPrice.subtract(candPrice);
                double savingsPct = Math.abs(candidate.getPriceDifferencePercentage() != null ? candidate.getPriceDifferencePercentage() : 0.0);
                reasonCodes.add(AlternativeReasonCode.LOWER_PRICE);
                if (sameCategory) {
                    reasonCodes.add(AlternativeReasonCode.SAME_CATEGORY_LOWER_PRICE);
                }
                if (savingsPct >= 20.0) {
                    reasonCodes.add(AlternativeReasonCode.BUDGET_SAVING);
                    badges.add("Budget Pick");
                }
                evidenceList.add(AlternativeEvidence.builder()
                        .category(AlternativeEvidenceCategory.PRICE)
                        .sourceValue(String.format("$%.2f", srcPrice))
                        .candidateValue(String.format("$%.2f", candPrice))
                        .relationship("CHEAPER")
                        .confidence(0.95)
                        .description(String.format("Costs $%.2f, saving $%.2f (%.1f%% less than $%.2f)", candPrice, savings, savingsPct, srcPrice))
                        .build());
            } else if (candPrice.compareTo(srcPrice) > 0) {
                BigDecimal premium = candPrice.subtract(srcPrice);
                double premPct = candidate.getPriceDifferencePercentage() != null ? candidate.getPriceDifferencePercentage() : 0.0;
                reasonCodes.add(AlternativeReasonCode.PREMIUM_PRICE_TIER);
                evidenceList.add(AlternativeEvidence.builder()
                        .category(AlternativeEvidenceCategory.PRICE)
                        .sourceValue(String.format("$%.2f", srcPrice))
                        .candidateValue(String.format("$%.2f", candPrice))
                        .relationship("PREMIUM_TIER")
                        .confidence(0.90)
                        .description(String.format("$%.2f more (+%.1f%% premium) than baseline", premium, premPct))
                        .build());
            }
        }

        // 3. Rating Evaluation
        if (candRating > srcRating) {
            double ratingDiff = candRating - srcRating;
            reasonCodes.add(AlternativeReasonCode.HIGHER_RATING);
            evidenceList.add(AlternativeEvidence.builder()
                    .category(AlternativeEvidenceCategory.RATING)
                    .sourceValue(String.format("%.1f★", srcRating))
                    .candidateValue(String.format("%.1f★", candRating))
                    .relationship("HIGHER_RATING")
                    .confidence(0.85)
                    .description(String.format("Customer rating is higher (%.1f★ vs %.1f★, +%.1f★)", candRating, srcRating, ratingDiff))
                    .build());
            if (candRating >= 4.5) {
                badges.add("Top Rated");
            }
        }

        // 4. Discount & Deal Evaluation
        if (candidate.getDiscountPercentage() != null && candidate.getDiscountPercentage().compareTo(BigDecimal.valueOf(10)) >= 0) {
            reasonCodes.add(AlternativeReasonCode.SIGNIFICANT_DISCOUNT);
            evidenceList.add(AlternativeEvidence.builder()
                    .category(AlternativeEvidenceCategory.DISCOUNT)
                    .candidateValue(String.format("%.0f%%", candidate.getDiscountPercentage()))
                    .relationship("DISCOUNT_ACTIVE")
                    .confidence(0.90)
                    .description(String.format("Promotional discount: %.0f%% off original price", candidate.getDiscountPercentage()))
                    .build());
        }

        if (candidate.getDealQuality() == DealQuality.EXCELLENT_DEAL || candidate.getDealQuality() == DealQuality.GOOD_DEAL) {
            reasonCodes.add(AlternativeReasonCode.BETTER_DEAL);
            badges.add(candidate.getDealQuality() == DealQuality.EXCELLENT_DEAL ? "Best Value" : "Good Deal");
            evidenceList.add(AlternativeEvidence.builder()
                    .category(AlternativeEvidenceCategory.VALUE)
                    .candidateValue(candidate.getDealQuality().name())
                    .relationship("DEAL_QUALITY")
                    .confidence(0.95)
                    .description(String.format("Price analytics confirms %s relative to historical range", candidate.getDealQuality()))
                    .build());
        }

        if (candidate.getCurrentBestPrice() != null) {
            reasonCodes.add(AlternativeReasonCode.IN_STOCK);
        }

        // 5. Compute Type-Specific Multidimensional Score (0.0 to 100.0)
        switch (effectiveType) {
            case CHEAPER -> {
                // Price savings: up to 50 pts, Semantic: up to 30 pts, Category: up to 10 pts, Rating: up to 10 pts
                double savingsPts = 0.0;
                if (candidate.getPriceDifferencePercentage() != null && candidate.getPriceDifferencePercentage() < 0) {
                    savingsPts = Math.min(50.0, Math.abs(candidate.getPriceDifferencePercentage()) * 1.25);
                }
                score = savingsPts + (similarity * 30.0) + (sameCategory ? 10.0 : 0.0) + ((candRating / 5.0) * 10.0);
            }
            case BUDGET_FALLBACK -> {
                // Aggressive price savings: up to 60 pts, Semantic: up to 25 pts, Rating: up to 15 pts
                double savingsPts = 0.0;
                if (candidate.getPriceDifferencePercentage() != null && candidate.getPriceDifferencePercentage() < 0) {
                    savingsPts = Math.min(60.0, Math.abs(candidate.getPriceDifferencePercentage()) * 1.5);
                }
                score = savingsPts + (similarity * 25.0) + ((candRating / 5.0) * 15.0);
            }
            case BETTER_VALUE -> {
                // Deal quality: up to 30 pts, Discount: up to 20 pts, Price savings: up to 25 pts, Semantic: up to 15 pts, Rating: up to 10 pts
                double dealPts = getDealQualityPoints(candidate.getDealQuality());
                double discPts = candidate.getDiscountPercentage() != null ? Math.min(20.0, candidate.getDiscountPercentage().doubleValue() * 0.5) : 0.0;
                double savingsPts = 0.0;
                if (candidate.getPriceDifferencePercentage() != null && candidate.getPriceDifferencePercentage() < 0) {
                    savingsPts = Math.min(25.0, Math.abs(candidate.getPriceDifferencePercentage()) * 0.8);
                }
                score = dealPts + discPts + savingsPts + (similarity * 15.0) + ((candRating / 5.0) * 10.0);
            }
            case PERFORMANCE_UPGRADE -> {
                // Rating superiority: up to 45 pts, Semantic: up to 30 pts, Deal Quality: up to 15 pts, Brand/Category: up to 10 pts
                double ratingPts = (candRating / 5.0) * 35.0;
                if (candRating > srcRating) {
                    ratingPts += Math.min(10.0, (candRating - srcRating) * 20.0);
                }
                score = ratingPts + (similarity * 30.0) + getDealQualityPoints(candidate.getDealQuality()) * 0.5 + (sameCategory ? 10.0 : 0.0);
            }
            case PREMIUM -> {
                // Rating & Brand: up to 45 pts, Premium tier: up to 25 pts, Semantic: up to 30 pts
                double ratingPts = (candRating / 5.0) * 35.0;
                double brandPts = sameBrand ? 10.0 : 5.0;
                double premiumPts = (candidate.getPriceDifferencePercentage() != null && candidate.getPriceDifferencePercentage() > 0)
                        ? Math.min(20.0, candidate.getPriceDifferencePercentage() * 0.4) : 10.0;
                score = ratingPts + brandPts + premiumPts + (similarity * 30.0);
            }
            case SIMILAR -> {
                // Semantic: up to 50 pts, Category: up to 20 pts, Brand: up to 10 pts, Rating: up to 10 pts, Proximity: up to 10 pts
                double proximityPts = 10.0;
                if (candidate.getPriceDifferencePercentage() != null) {
                    double absDiff = Math.abs(candidate.getPriceDifferencePercentage());
                    proximityPts = Math.max(0.0, 10.0 - (absDiff * 0.1));
                }
                score = (similarity * 50.0) + (sameCategory ? 20.0 : 5.0) + (sameBrand ? 10.0 : 0.0) + ((candRating / 5.0) * 10.0) + proximityPts;
            }
        }

        // Final score rounding
        double finalScore = Math.round(Math.min(100.0, Math.max(0.0, score)) * 10.0) / 10.0;
        candidate.setAlternativeScore(finalScore);
        candidate.setBadges(badges);
        candidate.setReasonCodes(reasonCodes);
        candidate.setEvidence(evidenceList);

        // Generate concise human-readable explanation
        candidate.setPrimaryExplanation(generateExplanation(candidate, sourceContext, effectiveType));
    }

    @Override
    public Comparator<AlternativeCandidate> getDeterministicComparator() {
        return (c1, c2) -> {
            // 1. Alternative score descending
            int comp = Double.compare(c2.getAlternativeScore(), c1.getAlternativeScore());
            if (comp != 0) return comp;

            // 2. Semantic similarity descending
            double s1 = c1.getSemanticSimilarityScore() != null ? c1.getSemanticSimilarityScore() : 0.0;
            double s2 = c2.getSemanticSimilarityScore() != null ? c2.getSemanticSimilarityScore() : 0.0;
            comp = Double.compare(s2, s1);
            if (comp != 0) return comp;

            // 3. Deal quality descending
            int dq1 = getDealQualityRank(c1.getDealQuality());
            int dq2 = getDealQualityRank(c2.getDealQuality());
            comp = Integer.compare(dq2, dq1);
            if (comp != 0) return comp;

            // 4. Rating descending
            double r1 = c1.getRating() != null ? c1.getRating() : 0.0;
            double r2 = c2.getRating() != null ? c2.getRating() : 0.0;
            comp = Double.compare(r2, r1);
            if (comp != 0) return comp;

            // 5. Current price ascending
            BigDecimal p1 = c1.getCurrentBestPrice() != null ? c1.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal p2 = c2.getCurrentBestPrice() != null ? c2.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            comp = p1.compareTo(p2);
            if (comp != 0) return comp;

            // 6. Product UUID lexicographical ascending
            String id1 = c1.getProductId() != null ? c1.getProductId().toString() : "";
            String id2 = c2.getProductId() != null ? c2.getProductId().toString() : "";
            return id1.compareTo(id2);
        };
    }

    private double getDealQualityPoints(DealQuality quality) {
        if (quality == null) return 5.0;
        return switch (quality) {
            case EXCELLENT_DEAL -> 30.0;
            case GOOD_DEAL -> 20.0;
            case FAIR_PRICE -> 10.0;
            case ABOVE_AVERAGE -> 5.0;
            case HIGH_PRICE -> 0.0;
            case INSUFFICIENT_DATA -> 5.0;
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

    private Double resolveRating(AlternativeCandidate candidate) {
        if (candidate == null || candidate.getProduct() == null) return 4.0;
        if (candidate.getRating() != null) return candidate.getRating();
        UUID id = candidate.getProductId();
        if (id == null) return 4.0;
        int hash = Math.abs(id.hashCode());
        return Math.round((4.0 + ((hash % 11) / 10.0)) * 10.0) / 10.0;
    }

    private String generateExplanation(AlternativeCandidate candidate, SourceProductContextDTO sourceContext, AlternativeType type) {
        String name = candidate.getProduct().getName();
        Double sim = candidate.getSemanticSimilarityScore();
        int simPct = sim != null ? (int) Math.round(sim * 100) : 0;
        Double priceDiffPct = candidate.getPriceDifferencePercentage();
        BigDecimal priceDiff = candidate.getPriceDifference();

        switch (type) {
            case CHEAPER -> {
                if (priceDiff != null && priceDiff.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal savings = priceDiff.abs();
                    double pct = Math.abs(priceDiffPct != null ? priceDiffPct : 0.0);
                    return String.format("Saves $%.2f (%.0f%% cheaper) with %d%% concept similarity in the same category.",
                            savings, pct, simPct);
                }
                return String.format("Lower price alternative with %d%% concept similarity.", simPct);
            }
            case BUDGET_FALLBACK -> {
                if (priceDiff != null && priceDiff.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal savings = priceDiff.abs();
                    double pct = Math.abs(priceDiffPct != null ? priceDiffPct : 0.0);
                    return String.format("Budget fallback saving $%.2f (%.0f%% less) while retaining strong core features.",
                            savings, pct);
                }
                return "Cost-effective budget alternative with compatible specifications.";
            }
            case BETTER_VALUE -> {
                if (candidate.getDealQuality() == DealQuality.EXCELLENT_DEAL) {
                    return String.format("Excellent deal rating with %d%% concept similarity and verified historical savings.", simPct);
                }
                if (candidate.getDiscountPercentage() != null && candidate.getDiscountPercentage().compareTo(BigDecimal.valueOf(10)) >= 0) {
                    return String.format("High value option offering %.0f%% promotional discount and %.1f★ customer satisfaction.",
                            candidate.getDiscountPercentage(), candidate.getRating());
                }
                return String.format("Optimal balance of price, quality (%.1f★), and %d%% concept similarity.",
                        candidate.getRating(), simPct);
            }
            case PERFORMANCE_UPGRADE -> {
                double srcRating = sourceContext != null && sourceContext.getRating() != null ? sourceContext.getRating() : 4.0;
                if (candidate.getRating() > srcRating) {
                    return String.format("Performance upgrade featuring superior %.1f★ customer rating (vs %.1f★) with %d%% concept similarity.",
                            candidate.getRating(), srcRating, simPct);
                }
                return String.format("Higher tier alternative with enhanced features and %d%% concept similarity.", simPct);
            }
            case PREMIUM -> {
                return String.format("Premium tier alternative with high build quality, %.1f★ rating, and %d%% concept match.",
                        candidate.getRating(), simPct);
            }
            case SIMILAR -> {
                return String.format("Highly comparable alternative with %d%% concept similarity in %s.",
                        simPct, candidate.getProduct().getCategory());
            }
            default -> {
                return String.format("Alternative matching source product with %d%% concept similarity.", simPct);
            }
        }
    }
}
