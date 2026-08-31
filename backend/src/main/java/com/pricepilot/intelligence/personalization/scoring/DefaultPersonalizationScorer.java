package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DefaultPersonalizationScorer implements PersonalizationScorer {

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

    @Override
    public PersonalizationResult scorePersonalization(
            ProductResponseDTO product,
            ProductScore baseScore,
            UserShoppingPreferenceEntity preferences,
            UserShoppingSignals signals) {

        double base = baseScore != null ? baseScore.getOverallScore() : 75.0;
        double contribution = 0.0;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        List<EvidenceItem> positiveEvidence = new ArrayList<>();
        List<EvidenceItem> tradeOffs = new ArrayList<>();

        BigDecimal currentPrice = extractMinPrice(product);
        boolean inStock = isInStock(product);
        double discountPct = extractDiscountPercentage(product, baseScore);

        // 1. Explicit Preferences
        if (preferences != null) {
            // Category Match
            if (product.getCategory() != null && preferences.getPreferredCategories() != null) {
                boolean matchesCategory = preferences.getPreferredCategories().stream()
                        .anyMatch(c -> c.equalsIgnoreCase(product.getCategory().trim()));
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

            // Brand Match
            if (product.getBrand() != null && preferences.getPreferredBrands() != null) {
                boolean matchesBrand = preferences.getPreferredBrands().stream()
                        .anyMatch(b -> b.equalsIgnoreCase(product.getBrand().trim()));
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

            // Budget Match / Penalty
            if (currentPrice != null) {
                BigDecimal minB = preferences.getMinBudget();
                BigDecimal maxB = preferences.getMaxBudget();

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
                }
            }

            // Rating Threshold
            double rating = extractRating(product);
            if (preferences.getMinRating() != null) {
                if (rating >= preferences.getMinRating()) {
                    contribution += RATING_THRESHOLD_WEIGHT;
                    breakdown.put("RatingThresholdMatch", RATING_THRESHOLD_WEIGHT);
                    positiveEvidence.add(new EvidenceItem(
                            product.getId(),
                            product.getName(),
                            EvidenceType.RATING_CRITERIA_MET,
                            String.format("Rating of %.1f★ meets your minimum threshold of %.1f★", rating, preferences.getMinRating()),
                            "Rating",
                            rating,
                            preferences.getMinRating(),
                            true,
                            0.70
                    ));
                }
            }

            // Deal Sensitivity
            if (preferences.getDealSensitivity() == DealSensitivity.HIGH && discountPct >= 15.0) {
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

            // Availability Preference
            if (preferences.getAvailabilityPreference() == AvailabilityPreference.IN_STOCK_ONLY) {
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
        }

        // 2. Behavioral Signals
        if (signals != null) {
            if (product.getCategory() != null) {
                double catAffinity = signals.getCategoryAffinity(product.getCategory());
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

            if (product.getBrand() != null) {
                double brandAffinity = signals.getBrandAffinity(product.getBrand());
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
        }

        // Bound contribution and calculate final score
        double boundedContribution = Math.max(-30.0, Math.min(35.0, contribution));
        double roundedContribution = Math.round(boundedContribution * 10.0) / 10.0;
        double finalScore = Math.max(0.0, Math.min(100.0, Math.round((base + roundedContribution) * 10.0) / 10.0));

        return PersonalizationResult.builder()
                .baseScore(base)
                .personalizationContribution(roundedContribution)
                .finalScore(finalScore)
                .personalizationBreakdown(breakdown)
                .personalizationEvidence(positiveEvidence)
                .personalizationTradeOffs(tradeOffs)
                .build();
    }

    @Override
    public Comparator<ProductResponseDTO> getDeterministicPersonalizedComparator(
            Map<UUID, PersonalizationResult> results) {

        return (p1, p2) -> {
            PersonalizationResult r1 = results.get(p1.getId());
            PersonalizationResult r2 = results.get(p2.getId());

            double f1 = r1 != null ? r1.getFinalScore() : 0.0;
            double f2 = r2 != null ? r2.getFinalScore() : 0.0;
            int cmpFinal = Double.compare(f2, f1); // Descending
            if (cmpFinal != 0) return cmpFinal;

            // Tier 2: Base score descending
            double b1 = r1 != null ? r1.getBaseScore() : 0.0;
            double b2 = r2 != null ? r2.getBaseScore() : 0.0;
            int cmpBase = Double.compare(b2, b1);
            if (cmpBase != 0) return cmpBase;

            // Tier 3: Personalization contribution descending
            double c1 = r1 != null ? r1.getPersonalizationContribution() : 0.0;
            double c2 = r2 != null ? r2.getPersonalizationContribution() : 0.0;
            int cmpContrib = Double.compare(c2, c1);
            if (cmpContrib != 0) return cmpContrib;

            // Tier 4: Lowest price ascending
            BigDecimal pr1 = extractMinPrice(p1);
            BigDecimal pr2 = extractMinPrice(p2);
            if (pr1 != null && pr2 != null) {
                int cmpPrice = pr1.compareTo(pr2);
                if (cmpPrice != 0) return cmpPrice;
            }

            // Tier 5: Lexicographical UUID string ascending
            return p1.getId().compareTo(p2.getId());
        };
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
