package com.pricepilot.intelligence.assistant.intent;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShoppingIntentClassifier {

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:under|below|less than|budget of|max)\\s*\\$?(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

    public AssistantIntent classifyIntent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return AssistantIntent.GENERAL;
        }

        String lower = text.toLowerCase(Locale.ROOT).trim();

        // 1. Comparison Intent
        if (lower.contains("compare") || lower.contains(" vs ") || lower.contains(" versus ")
                || lower.contains("difference between") || lower.contains("which is better")
                || lower.contains("which one should i choose") || lower.contains("side by side")) {
            return AssistantIntent.COMPARISON;
        }

        // 2. Watchlist / Alert Actions (check before price analysis so 'set alert when price drops' is categorized correctly)
        if (lower.contains("watchlist") || lower.contains("track price")
                || lower.contains("set alert") || lower.contains("notify me")
                || lower.contains("price alert") || lower.contains("alert when")
                || lower.contains("target price") || lower.contains("track this")) {
            return AssistantIntent.WATCHLIST_ACTION;
        }

        // 3. Personalized Recommendation
        if (lower.contains("recommend") || lower.contains("what should i")
                || lower.contains("suggest") || lower.contains("for me")
                || lower.contains("top pick") || lower.contains("personalized")) {
            return AssistantIntent.RECOMMENDATION;
        }

        // 4. Preference Query
        if (lower.contains("preference") || lower.contains("my budget") 
                || lower.contains("my settings") || lower.contains("current settings")
                || lower.contains("settings") || lower.contains("budget setting")
                || lower.contains("show my budget")) {
            return AssistantIntent.PREFERENCE_QUERY;
        }

        // 5. Price Analysis / Buy Timing Intent
        if (lower.contains("price history") || lower.contains("good time to buy")
                || lower.contains("buy now") || lower.contains("should i buy now")
                || lower.contains("wait for price drop") || lower.contains("lowest price")
                || lower.contains("price drop") || lower.contains("historical low")
                || lower.contains("price trend") || lower.contains("is it worth buying")
                || lower.contains("will the price drop")) {
            return AssistantIntent.PRICE_ANALYSIS;
        }

        // 6. Discovery / Search
        if (lower.contains("find") || lower.contains("search") || lower.contains("show me")
                || lower.contains("looking for") || lower.contains("under $") || lower.contains("under ")
                || lower.contains("laptop") || lower.contains("phone") || lower.contains("smartphone")
                || lower.contains("headphone") || lower.contains("console") || lower.contains("cheapest")
                || lower.contains("best deal")) {
            return AssistantIntent.DISCOVERY;
        }

        return AssistantIntent.GENERAL;
    }

    public Double extractPriceConstraint(String text) {
        if (text == null) return null;
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
