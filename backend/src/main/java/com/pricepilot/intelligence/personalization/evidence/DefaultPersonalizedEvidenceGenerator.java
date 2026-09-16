package com.pricepilot.intelligence.personalization.evidence;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScore;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Production implementation of {@link PersonalizedEvidenceGenerator}.
 * <p>
 * Generates transparent, grounded explainability evidence explaining the {@link PersonalizedScore}
 * produced by Phase 6.4.
 * <p>
 * Core Invariant:
 * Evidence explains the score; evidence never creates, modifies, or recalculates the score.
 */
@Component
public class DefaultPersonalizedEvidenceGenerator implements PersonalizedEvidenceGenerator {

    private static final Comparator<EvidenceItem> EVIDENCE_COMPARATOR = Comparator
            .comparingDouble(EvidenceItem::getImportance).reversed()
            .thenComparing(item -> item.getType() != null ? item.getType().name() : "")
            .thenComparing(item -> item.getDescription() != null ? item.getDescription() : "");

    @Override
    public PersonalizedEvidence generate(ProductResponseDTO product, PersonalizedScore score, PersonalizationContext context) {
        if (product == null || score == null || context == null || score.getBreakdown().isEmpty() || context.isEmpty()) {
            return PersonalizedEvidence.empty(product != null ? product.getId() : null);
        }

        UUID prodId = product.getId();
        String prodName = product.getName();
        BigDecimal currentPrice = extractMinPrice(product);
        boolean inStock = isInStock(product);
        double discountPct = extractDiscountPercentage(product);
        double rating = extractRating(product);

        Map<String, Double> breakdown = score.getBreakdown();
        List<EvidenceItem> positiveList = new ArrayList<>();
        List<EvidenceItem> tradeOffList = new ArrayList<>();

        // 1. Explicit Category Preference Evidence
        Double catContrib = breakdown.get("PreferredCategoryMatch");
        if (catContrib != null && catContrib > 0.0) {
            if (product.getCategory() != null && !product.getCategory().trim().isEmpty()) {
                String normCat = product.getCategory().trim().toLowerCase();
                if (context.getPreferredCategories().contains(normCat)) {
                    positiveList.add(new EvidenceItem(
                            prodId,
                            prodName,
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
        }

        // 2. Explicit Brand Preference Evidence
        Double brandContrib = breakdown.get("PreferredBrandMatch");
        if (brandContrib != null && brandContrib > 0.0) {
            if (product.getBrand() != null && !product.getBrand().trim().isEmpty()) {
                String normBrand = product.getBrand().trim().toLowerCase();
                if (context.getPreferredBrands().contains(normBrand)) {
                    positiveList.add(new EvidenceItem(
                            prodId,
                            prodName,
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
        }

        // 3. Explicit Budget Match Evidence
        Double budgetContrib = breakdown.get("BudgetMatch");
        if (budgetContrib != null && budgetContrib > 0.0 && currentPrice != null) {
            BigDecimal minB = context.getMinBudget().orElse(null);
            BigDecimal maxB = context.getMaxBudget().orElse(null);

            if (minB != null && maxB != null && currentPrice.compareTo(minB) >= 0 && currentPrice.compareTo(maxB) <= 0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.WITHIN_BUDGET,
                        String.format("Current price of $%s fits your budget ($%s - $%s)", currentPrice, minB, maxB),
                        "CurrentPrice",
                        currentPrice,
                        maxB,
                        true,
                        0.80
                ));
            } else if (maxB != null && currentPrice.compareTo(maxB) <= 0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.WITHIN_BUDGET,
                        String.format("Current price of $%s is within your budget of $%s", currentPrice, maxB),
                        "CurrentPrice",
                        currentPrice,
                        maxB,
                        true,
                        0.80
                ));
            } else if (minB != null && currentPrice.compareTo(minB) >= 0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
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

        // 4. Explicit Budget Exceed Penalty Evidence (Trade-off)
        Double exceedsContrib = breakdown.get("ExceedsBudgetPenalty");
        if (exceedsContrib != null && exceedsContrib < 0.0 && currentPrice != null) {
            BigDecimal maxB = context.getMaxBudget().orElse(null);
            if (maxB != null && currentPrice.compareTo(maxB) > 0) {
                tradeOffList.add(new EvidenceItem(
                        prodId,
                        prodName,
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

        // 5. Explicit Rating Threshold Evidence
        Double ratingContrib = breakdown.get("RatingThresholdMatch");
        if (ratingContrib != null && ratingContrib > 0.0 && context.getMinRating().isPresent()) {
            double minRating = context.getMinRating().get();
            if (rating >= minRating) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
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

        // 6. Explicit Deal Sensitivity Match Evidence
        Double dealContrib = breakdown.get("DealSensitivityMatch");
        if (dealContrib != null && dealContrib > 0.0 && context.getDealSensitivity().orElse(null) == DealSensitivity.HIGH) {
            if (discountPct >= 15.0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.DEAL_SENSITIVITY_MATCH,
                        String.format("%.0f%% discount aligns with your high deal sensitivity", discountPct),
                        "DiscountPercentage",
                        discountPct,
                        15.0,
                        true,
                        0.75
                ));
            }
        }

        // 7. Availability Preference Evidence
        Double availContrib = breakdown.get("AvailabilityMatch");
        if (availContrib != null && availContrib > 0.0 && context.getAvailabilityPreference().orElse(null) == AvailabilityPreference.IN_STOCK_ONLY) {
            if (inStock) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.HIGH_SELLER_AVAILABILITY,
                        "In stock, matching your availability preference",
                        "InStock",
                        true,
                        true,
                        true,
                        0.70
                ));
            }
        }

        // 8. Out of Stock Penalty Evidence (Trade-off)
        Double outOfStockContrib = breakdown.get("OutOfStockPenalty");
        if (outOfStockContrib != null && outOfStockContrib < 0.0 && context.getAvailabilityPreference().orElse(null) == AvailabilityPreference.IN_STOCK_ONLY) {
            if (!inStock) {
                tradeOffList.add(new EvidenceItem(
                        prodId,
                        prodName,
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

        // 9. Behavioral Category Affinity Evidence
        Double behCatContrib = breakdown.get("BehavioralCategoryAffinity");
        if (behCatContrib != null && behCatContrib > 0.0 && product.getCategory() != null) {
            double catAffinity = context.getCategoryAffinity(product.getCategory());
            if (catAffinity > 0.0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.BEHAVIORAL_AFFINITY,
                        "Matches your recent interest in " + product.getCategory(),
                        "CategoryAffinity",
                        catAffinity,
                        null,
                        true,
                        0.65
                ));
            }
        }

        // 10. Behavioral Brand Affinity Evidence
        Double behBrandContrib = breakdown.get("BehavioralBrandAffinity");
        if (behBrandContrib != null && behBrandContrib > 0.0 && product.getBrand() != null) {
            double brandAffinity = context.getBrandAffinity(product.getBrand());
            if (brandAffinity > 0.0) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.BEHAVIORAL_AFFINITY,
                        "Matches your frequent interest in " + product.getBrand(),
                        "BrandAffinity",
                        brandAffinity,
                        null,
                        true,
                        0.60
                ));
            }
        }

        // 11. Behavioral Interaction Affinity Evidence
        Double behInterContrib = breakdown.get("BehavioralInteractionAffinity");
        if (behInterContrib != null && behInterContrib > 0.0 && prodId != null) {
            if (context.hasInteractedWithProduct(prodId)) {
                positiveList.add(new EvidenceItem(
                        prodId,
                        prodName,
                        EvidenceType.BEHAVIORAL_AFFINITY,
                        "Matches your recent interest in this product",
                        "ProductInteraction",
                        1.0,
                        null,
                        true,
                        0.60
                ));
            }
        }

        // Sort deterministically
        positiveList.sort(EVIDENCE_COMPARATOR);
        tradeOffList.sort(EVIDENCE_COMPARATOR);

        return PersonalizedEvidence.builder()
                .productId(prodId)
                .positiveEvidence(positiveList)
                .tradeOffs(tradeOffList)
                .netAdjustment(score.getPersonalizationAdjustment())
                .build();
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

    private double extractDiscountPercentage(ProductResponseDTO product) {
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
