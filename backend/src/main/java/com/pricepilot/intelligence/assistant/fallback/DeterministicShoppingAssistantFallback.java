package com.pricepilot.intelligence.assistant.fallback;

import com.pricepilot.currency.CurrencyCode;
import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Component
public class DeterministicShoppingAssistantFallback {

    public String generateFallbackResponse(
            AssistantIntent intent,
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences) {

        StringBuilder sb = new StringBuilder();

        switch (intent) {
            case COMPARISON -> generateComparisonResponse(sb, bundle, preferences);
            case PRICE_ANALYSIS -> generatePriceAnalysisResponse(sb, bundle, preferences);
            case RECOMMENDATION -> generateRecommendationResponse(sb, bundle, preferences);
            case DISCOVERY -> generateDiscoveryResponse(sb, bundle, preferences);
            case WATCHLIST_ACTION -> generateWatchlistResponse(sb, bundle, preferences);
            case PREFERENCE_QUERY -> generatePreferenceQueryResponse(sb, preferences);
            default -> generateGeneralResponse(sb, bundle, preferences);
        }

        // Section: Personalization Reasoning (if any)
        if (bundle != null && bundle.getPersonalizationReasoning() != null && !bundle.getPersonalizationReasoning().isEmpty()) {
            sb.append("\n\n**Personalization Notes:**\n");
            for (PersonalizationReasoningItem item : bundle.getPersonalizationReasoning()) {
                sb.append("- ").append(item.getExplanation()).append("\n");
            }
        }

        // Section: Trade-Offs (if any)
        if (bundle != null && bundle.getTradeOffs() != null && !bundle.getTradeOffs().isEmpty()) {
            sb.append("\n\n**Key Trade-Offs & Considerations:**\n");
            for (TradeOffItem item : bundle.getTradeOffs()) {
                sb.append("- ").append(item.getDescription()).append("\n");
            }
        }

        // Section: Unknown / Insufficient Data Conditions (if any and not already incorporated into main body)
        if (bundle != null && bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
            boolean alreadyPrintedAsMainBody = (intent == AssistantIntent.DISCOVERY && (bundle.getGroundedProducts() == null || bundle.getGroundedProducts().isEmpty()) && (bundle.getFactualEvidence() == null || bundle.getFactualEvidence().isEmpty()))
                    || (intent == AssistantIntent.PRICE_ANALYSIS && (bundle.getGroundedProducts() == null || bundle.getGroundedProducts().isEmpty()) && (bundle.getFactualEvidence() == null || bundle.getFactualEvidence().isEmpty()))
                    || (intent == AssistantIntent.COMPARISON && (bundle.getGroundedProducts() == null || bundle.getGroundedProducts().isEmpty()) && (bundle.getFactualEvidence() == null || bundle.getFactualEvidence().isEmpty()));
            if (!alreadyPrintedAsMainBody) {
                sb.append("\n\n*Data Notes:* ");
                sb.append(String.join("; ", bundle.getUnknownOrInsufficientData())).append(".\n");
            }
        }

        return sb.toString().trim();
    }

    private void generateComparisonResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        sb.append("### Product Comparison & Decision Analysis\n\n");
        boolean hasMultipleProducts = bundle != null && bundle.getGroundedProducts() != null && bundle.getGroundedProducts().size() >= 2;
        boolean hasAnyProducts = bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty();
        boolean hasFactual = bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty();

        if (hasMultipleProducts || hasFactual) {
            sb.append("Here is the deterministic comparison based on verified catalog and pricing data:\n\n");
            if (hasFactual) {
                for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                    sb.append("- **").append(item.getProductName()).append("**: ")
                            .append(item.getDescription()).append("\n");
                }
            }
            if (hasAnyProducts) {
                sb.append("\n**Compared Products:**\n");
                appendProductsList(sb, bundle, preferences);
            }
        } else if (hasAnyProducts) {
            sb.append("Comparison requires at least 2 distinct products. Identified candidate:\n\n");
            appendProductsList(sb, bundle, preferences);
            if (bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
                sb.append("\n").append(String.join(". ", bundle.getUnknownOrInsufficientData())).append(".\n");
            } else {
                sb.append("\nPlease provide an additional product name or broaden your criteria to complete the comparison matrix.\n");
            }
        } else {
            if (bundle != null && bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
                sb.append(String.join(". ", bundle.getUnknownOrInsufficientData())).append(".\n");
            } else {
                sb.append("Comparison requires at least 2 distinct products meeting your criteria. Please provide 2 or more product names or broaden your budget criteria (e.g. *\"Compare Sony WH-1000XM5 and Bose QuietComfort Ultra\"*).\n");
            }
        }
    }

    private void generatePriceAnalysisResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        String pName = null;
        if (bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty()) {
            Map<String, Object> prod = bundle.getGroundedProducts().get(0);
            pName = (String) prod.getOrDefault("productName", prod.getOrDefault("name", "Product"));
        } else if (bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty()) {
            pName = bundle.getFactualEvidence().get(0).getProductName();
        }

        boolean hasFactual = bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty();
        boolean hasProducts = bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty();

        if (pName != null && !pName.isEmpty() && (hasFactual || hasProducts)) {
            sb.append("### Price Intelligence & Purchase Timing Analysis for ").append(pName).append("\n\n");
            if (hasFactual) {
                for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                    sb.append("- ").append(item.getDescription()).append("\n");
                }
            }
            if (hasProducts) {
                sb.append("\n**Analyzed Product:**\n");
                appendProductsList(sb, bundle, preferences);
            }
        } else {
            sb.append("### Price Intelligence & Purchase Timing Analysis\n\n");
            if (bundle != null && bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
                sb.append(String.join(". ", bundle.getUnknownOrInsufficientData())).append(".\n\n");
                sb.append("Please specify an existing catalog product name or select an active product.");
            } else {
                sb.append("Please specify a product name or select an active product so I can retrieve historical price trends and evaluate buy signals for you.");
            }
        }
    }

    private void generateRecommendationResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        boolean hasTrending = (bundle != null && bundle.getFactualEvidence() != null
                && bundle.getFactualEvidence().stream().anyMatch(e -> "TRENDING_PRODUCT".equalsIgnoreCase(e.getFactType())))
                || (bundle != null && bundle.getSuggestedActions() != null
                && bundle.getSuggestedActions().stream().anyMatch(a -> "EXPLORE_TRENDING".equalsIgnoreCase(a.getType())));

        boolean hasProducts = bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty();
        boolean hasFactual = bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty();

        if (hasTrending) {
            sb.append("### Trending Products & Popular Deals\n\n");
            if (hasProducts || hasFactual) {
                sb.append("Here are the top trending and high-interest products currently tracked on PricePilot:\n\n");
                if (hasFactual && !hasProducts) {
                    for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                        sb.append("- **").append(item.getProductName()).append("**: ").append(item.getDescription()).append("\n");
                    }
                }
                if (hasProducts) {
                    appendProductsList(sb, bundle, preferences);
                }
            } else {
                sb.append("Trending products are currently updating in our catalog. Explore the trending dashboard for live deals.\n");
            }
        } else {
            sb.append("### Personalized Shopping Recommendations\n\n");
            if (hasProducts || hasFactual) {
                sb.append("Based on your configured shopping preferences and deterministic comparison scores, here are the top recommendations:\n\n");
                if (hasFactual && !hasProducts) {
                    for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                        sb.append("- **").append(item.getProductName()).append("**: ").append(item.getDescription()).append("\n");
                    }
                }
                if (hasProducts) {
                    appendProductsList(sb, bundle, preferences);
                }
            } else {
                sb.append("No recommendations currently match your preferences. Consider updating your budget or preferred categories in settings.\n");
            }
        }
    }

    private void generateDiscoveryResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        sb.append("### Product Discovery Results\n\n");
        AssistantMatchClassification classification = bundle != null ? bundle.getMatchClassification() : null;
        String requestedEntity = bundle != null ? bundle.getRequestedEntity() : null;
        boolean hasProducts = bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty();
        boolean hasFactual = bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty();

        if (classification == AssistantMatchClassification.EXACT_MATCH) {
            sb.append("Exact match found in the verified catalog:\n\n");
            appendProductsList(sb, bundle, preferences);
        } else if (classification == AssistantMatchClassification.CLOSE_MATCHES) {
            if (requestedEntity != null && !requestedEntity.trim().isEmpty()) {
                sb.append("No exact ").append(requestedEntity).append(" was found in the verified catalog.\n\n");
            } else {
                sb.append("No exact match was found in the verified catalog.\n\n");
            }
            sb.append("Closest available matches:\n");
            appendProductsList(sb, bundle, preferences);
        } else if (classification == AssistantMatchClassification.NO_MATCH || (!hasProducts && !hasFactual)) {
            sb.append("I couldn't find an exact match or sufficiently close product in the verified catalog.\n\n");
            if (bundle != null && bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
                sb.append(String.join(". ", bundle.getUnknownOrInsufficientData())).append(".\n\n");
            } else {
                sb.append("Consider broadening your budget or search terms.\n\n");
            }
        } else {
            // CATEGORY_RESULTS or general discovery
            if (hasProducts || hasFactual) {
                sb.append("I found the following matching products in our verified catalog:\n\n");
                if (hasFactual && !hasProducts) {
                    for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                        sb.append("- **").append(item.getProductName()).append("**: ").append(item.getDescription()).append("\n");
                    }
                }
                if (hasProducts) {
                    appendProductsList(sb, bundle, preferences);
                }
            } else {
                sb.append("I couldn't find an exact match or sufficiently close product in the verified catalog.\n\n");
                if (bundle != null && bundle.getUnknownOrInsufficientData() != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
                    sb.append(String.join(". ", bundle.getUnknownOrInsufficientData())).append(".\n\n");
                } else {
                    sb.append("Consider broadening your budget or search terms.\n\n");
                }
            }
        }
    }

    private void appendProductsList(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        if (bundle == null || bundle.getGroundedProducts() == null || bundle.getGroundedProducts().isEmpty()) {
            return;
        }

        CurrencyCode fallbackCur = resolveDisplayCurrency(bundle, preferences);

        int index = 1;
        for (Map<String, Object> prod : bundle.getGroundedProducts()) {
            String name = (String) prod.getOrDefault("productName", prod.getOrDefault("name", "Product"));
            Object price = prod.getOrDefault("price", prod.get("currentPrice"));
            String brand = (String) prod.getOrDefault("brand", "");
            Object discount = prod.getOrDefault("discountPercentage", prod.get("discount"));

            CurrencyCode itemCurrency = fallbackCur;
            Object curCodeObj = prod.get("currency");
            if (curCodeObj != null) {
                itemCurrency = CurrencyCode.fromCode(curCodeObj.toString(), fallbackCur);
            }
            Object customSymbol = prod.get("currencySymbol");
            String sym = customSymbol != null ? customSymbol.toString() : itemCurrency.getSymbol();

            sb.append(index++).append(". **").append(name).append("**");
            if (brand != null && !brand.isEmpty()) {
                sb.append(" (").append(brand).append(")");
            }
            if (price instanceof Number pNum) {
                if (pNum.doubleValue() > 0) {
                    sb.append(" — ").append(sym).append(formatAmount(pNum.doubleValue(), itemCurrency));
                }
            } else if (price != null && !price.toString().isEmpty()) {
                sb.append(" — ").append(sym).append(price.toString());
            }
            if (discount instanceof Number d && d.doubleValue() > 0) {
                sb.append(" (").append(String.format(Locale.ROOT, "%.0f%% off", d.doubleValue())).append(")");
            }
            sb.append("\n");
        }
    }

    private void generateWatchlistResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        sb.append("### Price Watchlists & Alerts Status\n\n");
        if (bundle != null && bundle.getFactualEvidence() != null && !bundle.getFactualEvidence().isEmpty()) {
            for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                sb.append("- ").append(item.getDescription()).append("\n");
            }
        } else if (bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty()) {
            sb.append("Here are products relevant to your watchlist and alert actions:\n\n");
            appendProductsList(sb, bundle, preferences);
        } else {
            sb.append("You currently have no active watchlists or price alerts. You can track any product by clicking 'Track Price' or asking me to alert you when a price drops.");
        }
    }

    private void generatePreferenceQueryResponse(StringBuilder sb, UserShoppingPreferenceDTO prefs) {
        sb.append("### Your Current Shopping Preferences\n\n");
        if (prefs == null) {
            sb.append("You have not configured custom preferences yet. Default settings (neutral budget and medium deal sensitivity) are currently applied.\n");
            return;
        }

        CurrencyCode prefCur = prefs.getCurrency() != null ? prefs.getCurrency() : CurrencyCode.INR;
        String sym = prefCur.getSymbol();

        sb.append("- **Budget Range:** ");
        if (prefs.getMinBudget() != null && prefs.getMaxBudget() != null) {
            sb.append(sym).append(formatAmount(prefs.getMinBudget(), prefCur))
                    .append(" - ")
                    .append(sym).append(formatAmount(prefs.getMaxBudget(), prefCur))
                    .append("\n");
        } else if (prefs.getMaxBudget() != null) {
            sb.append("Up to ").append(sym).append(formatAmount(prefs.getMaxBudget(), prefCur)).append("\n");
        } else {
            sb.append("Flexible (no cap)\n");
        }

        sb.append("- **Preferred Categories:** ")
                .append(prefs.getPreferredCategories() != null && !prefs.getPreferredCategories().isEmpty()
                        ? String.join(", ", prefs.getPreferredCategories()) : "All categories")
                .append("\n");

        sb.append("- **Preferred Brands:** ")
                .append(prefs.getPreferredBrands() != null && !prefs.getPreferredBrands().isEmpty()
                        ? String.join(", ", prefs.getPreferredBrands()) : "All brands")
                .append("\n");

        sb.append("- **Deal Sensitivity:** ").append(prefs.getDealSensitivity() != null ? prefs.getDealSensitivity() : "MEDIUM").append("\n");
        sb.append("- **Stock Preference:** ").append(prefs.getAvailabilityPreference() != null ? prefs.getAvailabilityPreference() : "IN_STOCK_ONLY").append("\n");
    }

    private void generateGeneralResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        CurrencyCode cur = resolveDisplayCurrency(bundle, preferences);
        String examplePrice = switch (cur) {
            case INR -> "₹50000";
            case EUR -> "€1000";
            case GBP -> "£900";
            case JPY -> "¥150000";
            case USD -> "$1200";
        };

        sb.append("Hello! I am your PricePilot Shopping Assistant. I provide evidence-grounded decision support using verified price histories, comparison algorithms, and personalized preferences.\n\n")
                .append("You can ask me to:\n")
                .append("- **Find products**: *\"Find gaming laptops under ").append(examplePrice).append("\"*\n")
                .append("- **Compare choices**: *\"Compare products side by side\"*\n")
                .append("- **Analyze timing**: *\"Is now a good time to buy?\"*\n")
                .append("- **Personalized recommendations**: *\"Recommend the best products for my budget\"*\n")
                .append("- **View preferences**: *\"Show my shopping preferences\"*");
    }

    private CurrencyCode resolveDisplayCurrency(AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        if (preferences != null && preferences.getCurrency() != null) {
            return preferences.getCurrency();
        }
        if (bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty()) {
            Object curCodeObj = bundle.getGroundedProducts().get(0).get("currency");
            if (curCodeObj != null) {
                return CurrencyCode.fromCode(curCodeObj.toString(), CurrencyCode.INR);
            }
        }
        return CurrencyCode.INR;
    }

    private String formatAmount(double amount, CurrencyCode currency) {
        int precision = currency != null ? currency.getDecimalPrecision() : 2;
        if (precision == 0) {
            return String.format(Locale.ROOT, "%.0f", amount);
        }
        return String.format(Locale.ROOT, "%." + precision + "f", amount);
    }

    private String formatAmount(BigDecimal amount, CurrencyCode currency) {
        if (amount == null) return "0";
        return formatAmount(amount.doubleValue(), currency);
    }
}

