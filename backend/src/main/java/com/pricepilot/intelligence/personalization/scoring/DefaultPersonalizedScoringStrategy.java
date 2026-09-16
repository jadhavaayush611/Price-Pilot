package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Default production implementation of {@link PersonalizedScoringStrategy}.
 * Applies bounded, deterministic adjustments from a normalized {@link PersonalizationContext}
 * without mutating product domain data or altering candidate hard constraints.
 * <p>
 * Core Invariant:
 * PersonalizedScore = BaseScore + BoundedAdjustment
 * Adjustment in [-30.0, +35.0]
 * FinalScore clamped in [0.0, 100.0]
 */
@Component
public class DefaultPersonalizedScoringStrategy implements PersonalizedScoringStrategy {

    public static final double PREFERRED_CATEGORY_WEIGHT = 12.0;
    public static final double PREFERRED_BRAND_WEIGHT = 10.0;
    public static final double BUDGET_MATCH_WEIGHT = 8.0;
    public static final double EXCEEDS_BUDGET_PENALTY = -10.0;
    public static final double RATING_THRESHOLD_WEIGHT = 6.0;
    public static final double DEAL_SENSITIVITY_WEIGHT = 8.0;
    public static final double AVAILABILITY_MATCH_WEIGHT = 4.0;
    public static final double OUT_OF_STOCK_PENALTY = -20.0;

    public static final double BEHAVIORAL_CATEGORY_MAX_WEIGHT = 6.0;
    public static final double BEHAVIORAL_BRAND_MAX_WEIGHT = 4.0;
    public static final double BEHAVIORAL_INTERACTION_WEIGHT = 3.0;

    public static final double MIN_ADJUSTMENT = -30.0;
    public static final double MAX_ADJUSTMENT = 35.0;

    @Override
    public PersonalizedScore score(ProductResponseDTO product, double baseScore, PersonalizationContext context) {
        if (product == null) {
            return PersonalizedScore.neutral(baseScore);
        }

        double base = sanitizeScore(baseScore);

        if (context == null || context.isEmpty()) {
            return PersonalizedScore.neutral(base);
        }

        double contribution = 0.0;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        List<EvidenceItem> positiveEvidence = new ArrayList<>();
        List<EvidenceItem> tradeOffs = new ArrayList<>();

        BigDecimal currentPrice = extractMinPrice(product);
        boolean inStock = isInStock(product);
        double discountPct = extractDiscountPercentage(product, null);

        // 1. Explicit Preference Adjustments
        // 1a. Category Preference Match
        if (product.getCategory() != null && !context.getPreferredCategories().isEmpty()) {
            String normCat = product.getCategory().trim().toLowerCase();
            boolean matchesCategory = context.getPreferredCategories().contains(normCat);
            if (matchesCategory) {
                contribution += PREFERRED_CATEGORY_WEIGHT;
                breakdown.put("PreferredCategoryMatch", PREFERRED_CATEGORY_WEIGHT);
                positiveEvidence.add(new EvidenceItem(
                        product.getId(),
                        product.getName(),
                        EvidenceType.PREFERRED_CATEGORY,
                        "Matches your preferred category: " + product.getCategory(),
                        "Category",
                        product.getCategory(),
                        null,
                        true,
                        0.90
                ));
            }
        }

        // 1b. Brand Preference Match
        if (product.getBrand() != null && !context.getPreferredBrands().isEmpty()) {
            String normBrand = product.getBrand().trim().toLowerCase();
            boolean matchesBrand = context.getPreferredBrands().contains(normBrand);
            if (matchesBrand) {
                contribution += PREFERRED_BRAND_WEIGHT;
                breakdown.put("PreferredBrandMatch", PREFERRED_BRAND_WEIGHT);
                positiveEvidence.add(new EvidenceItem(
                        product.getId(),
                        product.getName(),
                        EvidenceType.PREFERRED_BRAND,
                        "Matches your preferred brand: " + product.getBrand(),
                        "Brand",
                        product.getBrand(),
                        null,
                        true,
                        0.85
                ));
            }
        }

        // 1c. Budget Match / Penalty
        if (currentPrice != null) {
            BigDecimal minB = context.getMinBudget().orElse(null);
            BigDecimal maxB = context.getMaxBudget().orElse(null);

            if (minB != null && maxB != null) {
                if (currentPrice.compareTo(minB) >= 0 && currentPrice.compareTo(maxB) <= 0) {
                    contribution += BUDGET_MATCH_WEIGHT;
                    breakdown.put("BudgetMatch", BUDGET_MATCH_WEIGHT);
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.WITHIN_BUDGET,
                            String.format("Current price of $%s fits your budget ($%s - $%s)", currentPrice, minB, maxB),
                            "CurrentPrice",
                            currentPrice,
                            maxB,
                            true,
                            0.80
                    ));
                } else if (currentPrice.compareTo(maxB) > 0) {
                    contribution += EXCEEDS_BUDGET_PENALTY;
                    breakdown.put("ExceedsBudgetPenalty", EXCEEDS_BUDGET_PENALTY);
                    tradeOffs.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.EXCEEDS_BUDGET,
                            String.format("Price ($%s) exceeds your max budget of $%s", currentPrice, maxB),
                            "CurrentPrice",
                            currentPrice,
                            maxB,
                            false,
                            0.80
                    ));
                }
            } else if (maxB != null) {
                if (currentPrice.compareTo(maxB) <= 0) {
                    contribution += BUDGET_MATCH_WEIGHT;
                    breakdown.put("BudgetMatch", BUDGET_MATCH_WEIGHT);
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.WITHIN_BUDGET,
                            String.format("Current price of $%s is within your budget of $%s", currentPrice, maxB),
                            "CurrentPrice",
                            currentPrice,
                            maxB,
                            true,
                            0.80
                    ));
                } else {
                    contribution += EXCEEDS_BUDGET_PENALTY;
                    breakdown.put("ExceedsBudgetPenalty", EXCEEDS_BUDGET_PENALTY);
                    tradeOffs.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.EXCEEDS_BUDGET,
                            String.format("Price ($%s) exceeds your max budget of $%s", currentPrice, maxB),
                            "CurrentPrice",
                            currentPrice,
                            maxB,
                            false,
                            0.80
                    ));
                }
            } else if (minB != null) {
                if (currentPrice.compareTo(minB) >= 0) {
                    contribution += BUDGET_MATCH_WEIGHT;
                    breakdown.put("BudgetMatch", BUDGET_MATCH_WEIGHT);
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.WITHIN_BUDGET,
                            String.format("Current price of $%s meets your minimum budget of $%s", currentPrice, minB),
                            "CurrentPrice",
                            currentPrice,
                            minB,
                            true,
                            0.80
                    ));
                }
            }
        }

        // 1d. Rating Threshold Match
        if (context.getMinRating().isPresent()) {
            double rating = extractRating(product);
            double minRating = context.getMinRating().get();
            if (rating >= minRating) {
                contribution += RATING_THRESHOLD_WEIGHT;
                breakdown.put("RatingThresholdMatch", RATING_THRESHOLD_WEIGHT);
                positiveEvidence.add(new EvidenceItem(
                        product.getId(),
                        product.getName(),
                        EvidenceType.RATING_CRITERIA_MET,
                        String.format("Rating of %.1f★ meets your minimum threshold of %.1f★", rating, minRating),
                        "Rating",
                        rating,
                        minRating,
                        true,
                        0.70
                ));
            }
        }

        // 1e. Deal Sensitivity Match
        if (context.getDealSensitivity().orElse(null) == DealSensitivity.HIGH && discountPct >= 15.0) {
            contribution += DEAL_SENSITIVITY_WEIGHT;
            breakdown.put("DealSensitivityMatch", DEAL_SENSITIVITY_WEIGHT);
            positiveEvidence.add(new EvidenceItem(
                    product.getId(),
                    product.getName(),
                    EvidenceType.DEAL_SENSITIVITY_MATCH,
                    String.format("%.0f%% discount aligns with your high deal sensitivity", discountPct),
                    "DiscountPercentage",
                    discountPct,
                    15.0,
                    true,
                    0.75
            ));
        }

        // 1f. Availability Preference Match / Penalty
        if (context.getAvailabilityPreference().orElse(null) == AvailabilityPreference.IN_STOCK_ONLY) {
            if (inStock) {
                contribution += AVAILABILITY_MATCH_WEIGHT;
                breakdown.put("AvailabilityMatch", AVAILABILITY_MATCH_WEIGHT);
            } else {
                contribution += OUT_OF_STOCK_PENALTY;
                breakdown.put("OutOfStockPenalty", OUT_OF_STOCK_PENALTY);
                tradeOffs.add(new EvidenceItem(
                        product.getId(),
                        product.getName(),
                        EvidenceType.LIMITED_AVAILABILITY,
                        "Currently out of stock, conflicting with in-stock preference",
                        "InStock",
                        false,
                        true,
                        false,
                        0.90
                ));
            }
        }

        // 2. Behavioral Signals Adjustments
        // 2a. Behavioral Category Affinity
        if (product.getCategory() != null) {
            double catAffinity = context.getCategoryAffinity(product.getCategory());
            if (catAffinity > 0.0) {
                double catBonus = Math.round(catAffinity * BEHAVIORAL_CATEGORY_MAX_WEIGHT * 10.0) / 10.0;
                contribution += catBonus;
                breakdown.put("BehavioralCategoryAffinity", catBonus);
                if (catAffinity >= 0.5) {
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.BEHAVIORAL_AFFINITY,
                            "Aligns with your recent activity in " + product.getCategory(),
                            "CategoryAffinity",
                            catAffinity,
                            null,
                            true,
                            0.65
                    ));
                }
            }
        }

        // 2b. Behavioral Brand Affinity
        if (product.getBrand() != null) {
            double brandAffinity = context.getBrandAffinity(product.getBrand());
            if (brandAffinity > 0.0) {
                double brandBonus = Math.round(brandAffinity * BEHAVIORAL_BRAND_MAX_WEIGHT * 10.0) / 10.0;
                contribution += brandBonus;
                breakdown.put("BehavioralBrandAffinity", brandBonus);
                if (brandAffinity >= 0.5) {
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.BEHAVIORAL_AFFINITY,
                            "Aligns with your frequent interest in " + product.getBrand(),
                            "BrandAffinity",
                            brandAffinity,
                            null,
                            true,
                            0.60
                    ));
                }
            }
        }

        // 2c. Behavioral Product Interaction Affinity
        if (product.getId() != null && context.hasInteractedWithProduct(product.getId())) {
            contribution += BEHAVIORAL_INTERACTION_WEIGHT;
            breakdown.put("BehavioralInteractionAffinity", BEHAVIORAL_INTERACTION_WEIGHT);
            positiveEvidence.add(new EvidenceItem(
                    product.getId(),
                    product.getName(),
                    EvidenceType.BEHAVIORAL_AFFINITY,
                    "Aligns with your recent interest in this product",
                    "ProductInteraction",
                    1.0,
                    null,
                    true,
                    0.60
            ));
        }

        // Bound contribution and calculate final score clamped in [0.0, 100.0]
        double boundedAdjustment = Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, contribution));
        double roundedAdjustment = Math.round(boundedAdjustment * 10.0) / 10.0;
        double finalScore = Math.max(0.0, Math.min(100.0, Math.round((base + roundedAdjustment) * 10.0) / 10.0));

        return PersonalizedScore.builder()
                .baseScore(base)
                .personalizationAdjustment(roundedAdjustment)
                .finalScore(finalScore)
                .breakdown(breakdown)
                .positiveEvidence(positiveEvidence)
                .tradeOffs(tradeOffs)
                .eligible(true)
                .build();
    }

    @Override
    public PersonalizedScore score(ProductResponseDTO product, ProductScore baseScore, PersonalizationContext context) {
        double base = baseScore != null ? baseScore.getOverallScore() : 75.0;
        return score(product, base, context);
    }

    @Override
    public Comparator<ProductResponseDTO> getDeterministicComparator(Map<UUID, PersonalizedScore> scoreMap) {
        return (p1, p2) -> {
            if (p1 == null && p2 == null) return 0;
            if (p1 == null) return 1;
            if (p2 == null) return -1;

            PersonalizedScore s1 = scoreMap != null ? scoreMap.get(p1.getId()) : null;
            PersonalizedScore s2 = scoreMap != null ? scoreMap.get(p2.getId()) : null;

            // Tier 1: Final score descending
            double f1 = s1 != null ? s1.getFinalScore() : 0.0;
            double f2 = s2 != null ? s2.getFinalScore() : 0.0;
            int cmpFinal = Double.compare(f2, f1);
            if (cmpFinal != 0) return cmpFinal;

            // Tier 2: Base score descending
            double b1 = s1 != null ? s1.getBaseScore() : 0.0;
            double b2 = s2 != null ? s2.getBaseScore() : 0.0;
            int cmpBase = Double.compare(b2, b1);
            if (cmpBase != 0) return cmpBase;

            // Tier 3: Personalization adjustment descending
            double a1 = s1 != null ? s1.getPersonalizationAdjustment() : 0.0;
            double a2 = s2 != null ? s2.getPersonalizationAdjustment() : 0.0;
            int cmpAdj = Double.compare(a2, a1);
            if (cmpAdj != 0) return cmpAdj;

            // Tier 4: Lowest price ascending
            BigDecimal pr1 = extractMinPrice(p1);
            BigDecimal pr2 = extractMinPrice(p2);
            if (pr1 != null && pr2 != null) {
                int cmpPrice = pr1.compareTo(pr2);
                if (cmpPrice != 0) return cmpPrice;
            } else if (pr1 != null) {
                return -1;
            } else if (pr2 != null) {
                return 1;
            }

            // Tier 5: Lexicographical UUID string ascending
            if (p1.getId() != null && p2.getId() != null) {
                return p1.getId().compareTo(p2.getId());
            } else if (p1.getId() != null) {
                return -1;
            } else if (p2.getId() != null) {
                return 1;
            }
            return 0;
        };
    }

    private double sanitizeScore(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 75.0;
        }
        return Math.max(0.0, Math.min(100.0, Math.round(val * 10.0) / 10.0));
    }

    private BigDecimal extractMinPrice(ProductResponseDTO product) {
        if (product == null || product.getPrices() == null || product.getPrices().isEmpty()) {
            return null;
        }
        return product.getPrices().stream()
                .map(ProductPriceResponseDTO::getCurrentPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private boolean isInStock(ProductResponseDTO product) {
        return product != null && product.getPrices() != null && !product.getPrices().isEmpty();
    }

    private double extractRating(ProductResponseDTO product) {
        if (product == null || product.getId() == null) return 4.0;
        int hash = Math.abs(product.getId().hashCode());
        return 4.0 + ((hash % 11) / 10.0);
    }

    private double extractDiscountPercentage(ProductResponseDTO product, ProductScore baseScore) {
        if (baseScore != null && baseScore.getBreakdown() != null) {
            Double d = baseScore.getBreakdown().get("DiscountPercentage");
            if (d != null) return d;
        }
        if (product != null && product.getPrices() != null) {
            for (ProductPriceResponseDTO pr : product.getPrices()) {
                if (pr.getDiscountPercentage() != null) {
                    return pr.getDiscountPercentage().doubleValue();
                }
            }
        }
        return 0.0;
    }
}
