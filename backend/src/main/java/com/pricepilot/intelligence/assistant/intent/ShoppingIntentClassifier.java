package com.pricepilot.intelligence.assistant.intent;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShoppingIntentClassifier {

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:under|below|less than|budget of|budget|max|up to|within|<=?)\\s*(?:₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp|¥|jpy)?\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE
    );

    public AssistantIntent classifyIntent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return AssistantIntent.GENERAL;
        }

        String lower = text.toLowerCase(Locale.ROOT).trim();

        // 1. Comparison Intent
        if (lower.contains("compare") || lower.contains(" vs ") || lower.contains(" versus ")
                || lower.contains("difference between") || lower.contains("which is better")
                || lower.contains("which one should i choose") || lower.contains("side by side")
                || lower.contains("which one")) {
            return AssistantIntent.COMPARISON;
        }

        // 2. Watchlist / Alert Actions
        if (lower.contains("watchlist") || lower.contains("track price")
                || lower.contains("set alert") || lower.contains("notify me")
                || lower.contains("price alert") || lower.contains("alert when")
                || lower.contains("target price") || lower.contains("track this")
                || lower.contains("add to watchlist")) {
            return AssistantIntent.WATCHLIST_ACTION;
        }

        // 3. Personalized Recommendation & Trending Intent
        if (lower.contains("recommend") || lower.contains("recommendation")
                || lower.contains("what should i") || lower.contains("suggest")
                || lower.contains("suggestion") || lower.contains("for me")
                || lower.contains("top pick") || lower.contains("top picks")
                || lower.contains("personalized") || lower.contains("trending")
                || lower.contains("popular") || lower.contains("top deal")
                || lower.contains("best seller") || lower.contains("hot product")
                || lower.contains("what to buy") || lower.contains("alternative")
                || lower.contains("alternatives") || lower.contains("similar to")
                || lower.contains("what is similar")) {
            return AssistantIntent.RECOMMENDATION;
        }

        // 4. Preference Query & Adjustment
        if (lower.contains("preference") || lower.contains("preferences")
                || lower.contains("my budget") || lower.contains("my settings")
                || lower.contains("current settings") || lower.contains("settings")
                || lower.contains("budget setting") || lower.contains("show my budget")
                || lower.contains("adjust") || lower.contains("shopping preference")) {
            return AssistantIntent.PREFERENCE_QUERY;
        }

        // 5. Price Analysis / Buy Timing Intent
        if (lower.contains("price history") || lower.contains("good time to buy")
                || lower.contains("buy now") || lower.contains("should i buy")
                || lower.contains("is now a good time") || lower.contains("wait for price drop")
                || lower.contains("lowest price") || lower.contains("price drop")
                || lower.contains("historical low") || lower.contains("price trend")
                || lower.contains("is it worth buying") || lower.contains("worth buying")
                || lower.contains("will the price drop") || lower.contains("buy confidence")
                || lower.contains("time to buy") || lower.contains("when to buy")) {
            return AssistantIntent.PRICE_ANALYSIS;
        }

        // 6. Discovery / Search / Product Inquiry
        if (lower.contains("find") || lower.contains("search") || lower.contains("show me")
                || lower.contains("tell me about") || lower.contains("what about") || lower.contains("how about")
                || lower.contains("tell me") || lower.contains("looking for") || lower.contains("under") || lower.contains("below")
                || lower.contains("laptop") || lower.contains("phone") || lower.contains("smartphone") || lower.contains("iphone")
                || lower.contains("headphone") || lower.contains("headphones") || lower.contains("console")
                || lower.contains("cheapest") || lower.contains("best deal") || lower.contains("browse")
                || lower.contains("discover")) {
            return AssistantIntent.DISCOVERY;
        }

        return AssistantIntent.GENERAL;
    }

    public Double extractPriceConstraint(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
