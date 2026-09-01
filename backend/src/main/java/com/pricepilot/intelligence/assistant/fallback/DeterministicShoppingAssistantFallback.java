package com.pricepilot.intelligence.assistant.fallback;

import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DeterministicShoppingAssistantFallback {

    public String generateFallbackResponse(
            AssistantIntent intent,
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences) {

        StringBuilder sb = new StringBuilder();

        switch (intent) {
            case COMPARISON -> generateComparisonResponse(sb, bundle);
            case PRICE_ANALYSIS -> generatePriceAnalysisResponse(sb, bundle);
            case RECOMMENDATION -> generateRecommendationResponse(sb, bundle, preferences);
            case DISCOVERY -> generateDiscoveryResponse(sb, bundle, preferences);
            case WATCHLIST_ACTION -> generateWatchlistResponse(sb, bundle);
            case PREFERENCE_QUERY -> generatePreferenceQueryResponse(sb, preferences);
            default -> generateGeneralResponse(sb, bundle);
        }

        // Section: Personalization Reasoning (if any)
        if (bundle != null && !bundle.getPersonalizationReasoning().isEmpty()) {
            sb.append("\n\n**Personalization Notes:**\n");
            for (PersonalizationReasoningItem item : bundle.getPersonalizationReasoning()) {
                sb.append("- ").append(item.getExplanation()).append("\n");
            }
        }

        // Section: Trade-Offs (if any)
        if (bundle != null && !bundle.getTradeOffs().isEmpty()) {
            sb.append("\n\n**Key Trade-Offs & Considerations:**\n");
            for (TradeOffItem item : bundle.getTradeOffs()) {
                sb.append("- ").append(item.getDescription()).append("\n");
            }
        }

        // Section: Unknown / Insufficient Data Conditions (if any)
        if (bundle != null && !bundle.getUnknownOrInsufficientData().isEmpty()) {
            sb.append("\n\n*Data Notes:* ");
            sb.append(String.join("; ", bundle.getUnknownOrInsufficientData())).append(".\n");
        }

        return sb.toString().trim();
    }

    private void generateComparisonResponse(StringBuilder sb, AssistantEvidenceBundle bundle) {
        sb.append("Here is the deterministic comparison based on verified catalog and pricing data:\n\n");
        if (bundle != null && !bundle.getFactualEvidence().isEmpty()) {
            for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                sb.append("- **").append(item.getProductName()).append("**: ")
                        .append(item.getDescription()).append("\n");
            }
        } else if (bundle != null && !bundle.getGroundedProducts().isEmpty()) {
            appendProductsList(sb, bundle);
        } else {
            sb.append("No active comparison products could be retrieved. Please provide 2 or more product names or IDs to compare.");
        }
    }

    private void generatePriceAnalysisResponse(StringBuilder sb, AssistantEvidenceBundle bundle) {
        sb.append("### Price Intelligence & Purchase Timing Analysis\n\n");
        if (bundle != null && !bundle.getFactualEvidence().isEmpty()) {
            for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                sb.append("- ").append(item.getDescription()).append("\n");
            }
        } else if (bundle != null && !bundle.getGroundedProducts().isEmpty()) {
            appendProductsList(sb, bundle);
        } else {
            sb.append("Insufficient price history points are currently recorded to compute volatility or trends for this item.");
        }
    }

    private void generateRecommendationResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        sb.append("### Personalized Shopping Recommendations\n\n");
        sb.append("Based on your configured shopping preferences and deterministic comparison scores, here are the top recommendations:\n\n");
        appendProductsList(sb, bundle);
    }

    private void generateDiscoveryResponse(StringBuilder sb, AssistantEvidenceBundle bundle, UserShoppingPreferenceDTO preferences) {
        sb.append("### Product Discovery Results\n\n");
        sb.append("I found the following matching products in our verified catalog:\n\n");
        appendProductsList(sb, bundle);
    }

    private void appendProductsList(StringBuilder sb, AssistantEvidenceBundle bundle) {
        if (bundle == null || bundle.getGroundedProducts().isEmpty()) {
            sb.append("No products currently match your exact criteria. Consider broadening your budget or search terms.");
            return;
        }

        int index = 1;
        for (Map<String, Object> prod : bundle.getGroundedProducts()) {
            String name = (String) prod.getOrDefault("productName", prod.getOrDefault("name", "Product"));
            Object price = prod.getOrDefault("price", prod.get("currentPrice"));
            String brand = (String) prod.getOrDefault("brand", "");
            sb.append(index++).append(". **").append(name).append("**");
            if (brand != null && !brand.isEmpty()) {
                sb.append(" (").append(brand).append(")");
            }
            if (price != null) {
                sb.append(" — $").append(price);
            }
            sb.append("\n");
        }
    }

    private void generateWatchlistResponse(StringBuilder sb, AssistantEvidenceBundle bundle) {
        sb.append("### Price Watchlists & Alerts Status\n\n");
        if (bundle != null && !bundle.getFactualEvidence().isEmpty()) {
            for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                sb.append("- ").append(item.getDescription()).append("\n");
            }
        } else if (bundle != null && !bundle.getGroundedProducts().isEmpty()) {
            sb.append("Here are products relevant to your watchlist and alert actions:\n\n");
            appendProductsList(sb, bundle);
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

        sb.append("- **Budget Range:** ");
        if (prefs.getMinBudget() != null && prefs.getMaxBudget() != null) {
            sb.append("$").append(prefs.getMinBudget()).append(" - $").append(prefs.getMaxBudget()).append("\n");
        } else if (prefs.getMaxBudget() != null) {
            sb.append("Up to $").append(prefs.getMaxBudget()).append("\n");
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

        sb.append("- **Deal Sensitivity:** ").append(prefs.getDealSensitivity()).append("\n");
        sb.append("- **Stock Preference:** ").append(prefs.getAvailabilityPreference()).append("\n");
    }

    private void generateGeneralResponse(StringBuilder sb, AssistantEvidenceBundle bundle) {
        sb.append("Hello! I am your PricePilot Shopping Assistant. I provide evidence-grounded decision support using verified price histories, comparison algorithms, and personalized preferences.\n\n")
                .append("You can ask me to:\n")
                .append("- **Find products**: *\"Find gaming laptops under $1200\"*\n")
                .append("- **Compare choices**: *\"Compare iPhone 16 and Samsung Galaxy S24\"*\n")
                .append("- **Analyze timing**: *\"Is now a good time to buy Sony WH-1000XM5?\"*\n")
                .append("- **Personalized recommendations**: *\"Recommend the best headphones for my budget\"*\n")
                .append("- **View preferences**: *\"Show my shopping preferences\"*");
    }
}
