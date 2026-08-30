package com.pricepilot.intelligence.discovery.interpretation;

import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight deterministic query interpreter.
 * Extracts structured shopping constraints (brand, category, price bounds, rating, availability, deals)
 * from normalized search strings without fabricating unparsed parameters.
 */
@Component
public class QueryInterpreter {

    private final QueryNormalizer queryNormalizer;

    // Price patterns
    private static final Pattern BETWEEN_PRICE_PATTERN =
            Pattern.compile("\\b(?:between)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:and|to)\\s+(\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern MAX_PRICE_PATTERN =
            Pattern.compile("\\b(?:under|below|less than|max|up to|budget|<=?)\\s+(\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern MIN_PRICE_PATTERN =
            Pattern.compile("\\b(?:above|over|more than|min|at least|>=?)\\s+(\\d+(?:\\.\\d+)?)\\b");

    // Rating patterns
    private static final Pattern RATING_PATTERN =
            Pattern.compile("\\b(?:above|min|at least)?\\s*([1-5](?:\\.[0-9])?)\\s*(?:\\+|star|stars|rating)\\b");

    // Availability patterns
    private static final Pattern IN_STOCK_PATTERN =
            Pattern.compile("\\b(?:in stock|available)\\b");

    // Deal intent patterns
    private static final Pattern DEAL_PATTERN =
            Pattern.compile("\\b(?:best deal|best value|best|deals|deal|cheap|cheapest|discounted|discount|on sale|sale|low price)\\b");

    // Canonical Brand Dictionary (lowercase -> Canonical Display)
    private static final Map<String, String> KNOWN_BRANDS = new LinkedHashMap<>();
    static {
        KNOWN_BRANDS.put("apple", "Apple");
        KNOWN_BRANDS.put("samsung", "Samsung");
        KNOWN_BRANDS.put("sony", "Sony");
        KNOWN_BRANDS.put("nintendo", "Nintendo");
        KNOWN_BRANDS.put("dell", "Dell");
        KNOWN_BRANDS.put("lenovo", "Lenovo");
        KNOWN_BRANDS.put("hp", "HP");
        KNOWN_BRANDS.put("asus", "Asus");
        KNOWN_BRANDS.put("bose", "Bose");
        KNOWN_BRANDS.put("logitech", "Logitech");
        KNOWN_BRANDS.put("microsoft", "Microsoft");
        KNOWN_BRANDS.put("google", "Google");
        KNOWN_BRANDS.put("lg", "LG");
    }

    // Category Aliases (Alias -> Canonical Category)
    private static final Map<String, String> CATEGORY_ALIASES = new LinkedHashMap<>();
    static {
        CATEGORY_ALIASES.put("smartphone", "Smartphone");
        CATEGORY_ALIASES.put("smartphones", "Smartphone");
        CATEGORY_ALIASES.put("phone", "Smartphone");
        CATEGORY_ALIASES.put("phones", "Smartphone");
        CATEGORY_ALIASES.put("iphone", "Smartphone");
        CATEGORY_ALIASES.put("mobile", "Smartphone");
        CATEGORY_ALIASES.put("android", "Smartphone");

        CATEGORY_ALIASES.put("laptop", "Laptop");
        CATEGORY_ALIASES.put("laptops", "Laptop");
        CATEGORY_ALIASES.put("macbook", "Laptop");
        CATEGORY_ALIASES.put("notebook", "Laptop");
        CATEGORY_ALIASES.put("chromebook", "Laptop");

        CATEGORY_ALIASES.put("headphone", "Headphones");
        CATEGORY_ALIASES.put("headphones", "Headphones");
        CATEGORY_ALIASES.put("earbuds", "Headphones");
        CATEGORY_ALIASES.put("earphones", "Headphones");
        CATEGORY_ALIASES.put("airpods", "Headphones");
        CATEGORY_ALIASES.put("headset", "Headphones");

        CATEGORY_ALIASES.put("gaming", "Gaming");
        CATEGORY_ALIASES.put("console", "Gaming");
        CATEGORY_ALIASES.put("switch", "Gaming");
        CATEGORY_ALIASES.put("playstation", "Gaming");
        CATEGORY_ALIASES.put("xbox", "Gaming");

        CATEGORY_ALIASES.put("electronics", "Electronics");
        CATEGORY_ALIASES.put("tablet", "Electronics");
        CATEGORY_ALIASES.put("ipad", "Electronics");
        CATEGORY_ALIASES.put("monitor", "Electronics");
        CATEGORY_ALIASES.put("tv", "Electronics");
    }

    public QueryInterpreter(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = queryNormalizer;
    }

    /**
     * Interprets a raw user query string into structured parameters.
     */
    public InterpretedQuery interpret(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return InterpretedQuery.builder()
                    .originalQuery("")
                    .normalizedQuery("")
                    .cleanSearchTerms("")
                    .searchTokens(Collections.emptyList())
                    .interpretationNotes(Collections.emptyList())
                    .build();
        }

        String normalized = queryNormalizer.normalize(rawQuery);
        String workingQuery = normalized;
        List<String> notes = new ArrayList<>();

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        Double minRating = null;
        Boolean inStockOnly = null;
        Boolean dealIntent = null;
        String detectedBrand = null;
        String detectedCategory = null;

        // 1. Detect "Between X and Y" price
        Matcher betweenMatcher = BETWEEN_PRICE_PATTERN.matcher(workingQuery);
        if (betweenMatcher.find()) {
            try {
                minPrice = new BigDecimal(betweenMatcher.group(1));
                maxPrice = new BigDecimal(betweenMatcher.group(2));
                notes.add("Price between " + minPrice + " and " + maxPrice);
                workingQuery = betweenMatcher.replaceFirst("");
            } catch (Exception ignored) {}
        }

        // 2. Detect Rating first so 'above 4.5 stars' is not consumed by min price pattern
        Matcher ratingMatcher = RATING_PATTERN.matcher(workingQuery);
        if (ratingMatcher.find()) {
            try {
                minRating = Double.parseDouble(ratingMatcher.group(1));
                notes.add("Minimum rating: " + minRating + "★");
                workingQuery = ratingMatcher.replaceFirst("");
            } catch (Exception ignored) {}
        }

        // 3. Detect "Under / Max X" price
        if (maxPrice == null) {
            Matcher maxMatcher = MAX_PRICE_PATTERN.matcher(workingQuery);
            if (maxMatcher.find()) {
                try {
                    maxPrice = new BigDecimal(maxMatcher.group(1));
                    notes.add("Maximum price: " + maxPrice);
                    workingQuery = maxMatcher.replaceFirst("");
                } catch (Exception ignored) {}
            }
        }

        // 4. Detect "Above / Min X" price
        if (minPrice == null) {
            Matcher minMatcher = MIN_PRICE_PATTERN.matcher(workingQuery);
            if (minMatcher.find()) {
                try {
                    minPrice = new BigDecimal(minMatcher.group(1));
                    notes.add("Minimum price: " + minPrice);
                    workingQuery = minMatcher.replaceFirst("");
                } catch (Exception ignored) {}
            }
        }

        // 5. Detect Availability intent
        Matcher stockMatcher = IN_STOCK_PATTERN.matcher(workingQuery);
        if (stockMatcher.find()) {
            inStockOnly = true;
            notes.add("In-stock items only");
            workingQuery = stockMatcher.replaceFirst("");
        }

        // 6. Detect Deal / Value intent
        Matcher dealMatcher = DEAL_PATTERN.matcher(workingQuery);
        if (dealMatcher.find()) {
            dealIntent = true;
            notes.add("Deal/Value intent detected");
            workingQuery = dealMatcher.replaceFirst("");
        }

        // 7. Detect Brand
        for (Map.Entry<String, String> entry : KNOWN_BRANDS.entrySet()) {
            Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            Matcher bm = brandPattern.matcher(workingQuery);
            if (bm.find()) {
                detectedBrand = entry.getValue();
                notes.add("Detected brand: " + detectedBrand);
                // We keep brand in workingQuery for text matching unless redundant
                break;
            }
        }

        // 8. Detect Category
        for (Map.Entry<String, String> entry : CATEGORY_ALIASES.entrySet()) {
            Pattern catPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            Matcher cm = catPattern.matcher(workingQuery);
            if (cm.find()) {
                detectedCategory = entry.getValue();
                notes.add("Detected category: " + detectedCategory);
                break;
            }
        }

        // Clean residual search terms
        String cleanTerms = queryNormalizer.normalize(workingQuery);
        List<String> tokens = Arrays.stream(cleanTerms.split("\\s+"))
                .filter(t -> !t.isEmpty())
                .filter(t -> !isStopWord(t))
                .toList();

        // If all tokens were stripped as stop words, retain clean terms tokens
        if (tokens.isEmpty() && !cleanTerms.isEmpty()) {
            tokens = Arrays.stream(cleanTerms.split("\\s+"))
                    .filter(t -> !t.isEmpty())
                    .toList();
        }

        return InterpretedQuery.builder()
                .originalQuery(rawQuery)
                .normalizedQuery(normalized)
                .cleanSearchTerms(cleanTerms)
                .searchTokens(tokens)
                .detectedBrand(detectedBrand)
                .detectedCategory(detectedCategory)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .inStockOnly(inStockOnly)
                .dealIntent(dealIntent)
                .interpretationNotes(notes)
                .build();
    }

    private static boolean isStopWord(String word) {
        return Set.of("the", "a", "an", "for", "with", "and", "or", "in", "at", "to", "by", "of", "from")
                .contains(word);
    }
}
