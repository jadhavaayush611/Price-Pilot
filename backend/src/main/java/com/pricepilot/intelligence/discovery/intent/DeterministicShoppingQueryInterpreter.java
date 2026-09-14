package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust deterministic shopping query interpreter.
 *
 * Translates natural-language shopping queries into structured ShoppingQueryIntent
 * without requiring external API keys or hosted NLP services.
 *
 * Guarantees:
 * 1. Strict reproducibility across runs and environments.
 * 2. Safe currency parsing (INR / ₹ / Rs / Rs. / USD / $ / EUR / € / GBP / £, 'k' shorthand, commas, decimals).
 * 3. Structured constraint extraction (price bounds, star ratings, minimum discount, in-stock, category, brand, sort intent).
 * 4. Conflict detection (e.g. minPrice > maxPrice) without arbitrary fabrication.
 * 5. Semantic text extraction separating structured filters from residual semantic query tokens.
 */
@Component
public class DeterministicShoppingQueryInterpreter implements ShoppingQueryInterpreter {

    private static final int MAX_INPUT_LENGTH = 10000;

    private final QueryNormalizer queryNormalizer;

    // Currency prefix or suffix pattern
    private static final String CURR_PREFIX = "(?:₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp)?\\s*";
    private static final String CURR_SUFFIX = "(?:\\s*(?:₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp))?";
    private static final String NUM_VAL = "((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*(k|thousand|lakh|lac)?";

    // 1. Rating Patterns (Must strictly require star/*/+ or rated prefix)
    private static final Pattern RATING_PATTERN = Pattern.compile(
            "\\b(?:(?:at least|min(?:imum)?|above|rated|rating(?: of)?)\\s+)?([1-5](?:\\.[0-9])?)\\s*(?:\\+|\\*)?\\s*(?:stars?|star rating|star)(?:\\s*(?:and above|or above|or higher|minimum))?\\b"
            + "|\\b([1-5](?:\\.[0-9])?)\\s*(?:\\+|\\*)"
            + "|\\b(?:rated|rating(?: of)?)\\s+([1-5](?:\\.[0-9])?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 2. Discount Patterns (Must strictly require % or percent)
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile(
            "\\b(?:at least|min(?:imum)?|above|with)?\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*(?:%|percent)\\s*(?:discount|off|price drop)?\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 3. Price Patterns
    private static final Pattern BETWEEN_PRICE_PATTERN = Pattern.compile(
            "\\b(?:between|from)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\s+(?:and|to|-)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "\\b(?:under|below|less than|max(?:imum)?|up to|budget(?: of)?|within|<=?)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MIN_PRICE_PATTERN = Pattern.compile(
            "\\b(?:above|over|more than|min(?:imum)?|at least|starting from|>=?)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 4. Availability Patterns
    private static final Pattern IN_STOCK_PATTERN = Pattern.compile(
            "\\b(?:in\\s*stock|available|ready\\s*to\\s*ship)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 5. Sort Intent Patterns
    private static final Pattern CHEAPEST_SORT_PATTERN = Pattern.compile(
            "\\b(?:cheapest|lowest\\s*price|most\\s*affordable)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EXPENSIVE_SORT_PATTERN = Pattern.compile(
            "\\b(?:most\\s*expensive|highest\\s*price|premium)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DISCOUNT_SORT_PATTERN = Pattern.compile(
            "\\b(?:highest\\s*discount|best\\s*discount|biggest\\s*discount|maximum\\s*discount|best\\s*deal|best\\s*deals)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NEWEST_SORT_PATTERN = Pattern.compile(
            "\\b(?:newest|latest|new\\s*arrivals?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 6. General Deal / Value Intent
    private static final Pattern DEAL_PATTERN = Pattern.compile(
            "\\b(?:deal|deals|on\\s*sale|discounted|sale|bargain|value\\s*for\\s*money)\\b",
            Pattern.CASE_INSENSITIVE
    );

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
        CATEGORY_ALIASES.put("mobiles", "Smartphone");
        CATEGORY_ALIASES.put("android", "Smartphone");

        CATEGORY_ALIASES.put("laptop", "Laptop");
        CATEGORY_ALIASES.put("laptops", "Laptop");
        CATEGORY_ALIASES.put("macbook", "Laptop");
        CATEGORY_ALIASES.put("notebook", "Laptop");
        CATEGORY_ALIASES.put("notebooks", "Laptop");
        CATEGORY_ALIASES.put("chromebook", "Laptop");

        CATEGORY_ALIASES.put("headphone", "Headphones");
        CATEGORY_ALIASES.put("headphones", "Headphones");
        CATEGORY_ALIASES.put("earbuds", "Headphones");
        CATEGORY_ALIASES.put("earphones", "Headphones");
        CATEGORY_ALIASES.put("airpods", "Headphones");
        CATEGORY_ALIASES.put("headset", "Headphones");
        CATEGORY_ALIASES.put("headsets", "Headphones");

        CATEGORY_ALIASES.put("gaming", "Gaming");
        CATEGORY_ALIASES.put("console", "Gaming");
        CATEGORY_ALIASES.put("consoles", "Gaming");
        CATEGORY_ALIASES.put("switch", "Gaming");
        CATEGORY_ALIASES.put("playstation", "Gaming");
        CATEGORY_ALIASES.put("xbox", "Gaming");

        CATEGORY_ALIASES.put("electronics", "Electronics");
        CATEGORY_ALIASES.put("tablet", "Electronics");
        CATEGORY_ALIASES.put("tablets", "Electronics");
        CATEGORY_ALIASES.put("ipad", "Electronics");
        CATEGORY_ALIASES.put("monitor", "Electronics");
        CATEGORY_ALIASES.put("monitors", "Electronics");
        CATEGORY_ALIASES.put("tv", "Electronics");
        CATEGORY_ALIASES.put("television", "Electronics");
    }

    public DeterministicShoppingQueryInterpreter(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = queryNormalizer;
    }

    @Override
    public ShoppingQueryIntent interpret(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return ShoppingQueryIntent.builder()
                    .rawQuery("")
                    .semanticQuery("")
                    .confidenceNotes(Collections.emptyList())
                    .build();
        }

        // Bounded input length for safety
        String sanitized = rawQuery.length() > MAX_INPUT_LENGTH
                ? rawQuery.substring(0, MAX_INPUT_LENGTH)
                : rawQuery;

        String workingQuery = sanitized;
        List<String> notes = new ArrayList<>();
        Map<String, String> detectedAttributes = new LinkedHashMap<>();

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        Double minRating = null;
        BigDecimal minDiscount = null;
        Boolean inStock = null;
        Boolean dealIntent = null;
        String sortIntent = null;
        String detectedBrand = null;
        String detectedCategory = null;
        boolean hasConflicts = false;
        String conflictDescription = null;

        // 1. Detect Rating (prioritized before min price so 'above 4 stars' is not captured as price)
        Matcher ratingMatcher = RATING_PATTERN.matcher(workingQuery);
        if (ratingMatcher.find()) {
            try {
                String ratingStr = ratingMatcher.group(1);
                if (ratingStr == null) ratingStr = ratingMatcher.group(2);
                if (ratingStr == null) ratingStr = ratingMatcher.group(3);

                if (ratingStr != null) {
                    double rating = Double.parseDouble(ratingStr);
                    if (rating >= 0.0 && rating <= 5.0) {
                        minRating = rating;
                        notes.add("Minimum rating: " + minRating + "★");
                    } else {
                        notes.add("Rating out of supported 0-5 range: " + rating);
                    }
                }
                workingQuery = ratingMatcher.replaceFirst(" ");
            } catch (Exception ignored) {}
        }

        // 2. Detect Discount requirement (prioritized before min price so 'at least 15% off' is not captured as price)
        Matcher discountMatcher = DISCOUNT_PATTERN.matcher(workingQuery);
        if (discountMatcher.find()) {
            try {
                BigDecimal disc = new BigDecimal(discountMatcher.group(1));
                if (disc.compareTo(BigDecimal.ZERO) >= 0 && disc.compareTo(BigDecimal.valueOf(100)) <= 0) {
                    minDiscount = disc;
                    notes.add("Minimum discount: " + minDiscount + "%");
                }
                workingQuery = discountMatcher.replaceFirst(" ");
            } catch (Exception ignored) {}
        }

        // 3. Detect "Between X and Y" price
        Matcher betweenMatcher = BETWEEN_PRICE_PATTERN.matcher(workingQuery);
        if (betweenMatcher.find()) {
            try {
                BigDecimal p1 = parseNumericPrice(betweenMatcher.group(1), betweenMatcher.group(2));
                BigDecimal p2 = parseNumericPrice(betweenMatcher.group(3), betweenMatcher.group(4));
                if (p1 != null && p2 != null) {
                    if (p1.compareTo(p2) > 0) {
                        hasConflicts = true;
                        conflictDescription = "Conflicting price range: lower bound (" + p1 + ") exceeds upper bound (" + p2 + ")";
                        notes.add("Conflict detected in price range");
                    } else {
                        minPrice = p1;
                        maxPrice = p2;
                        notes.add("Price range: " + minPrice + " to " + maxPrice);
                    }
                }
                workingQuery = betweenMatcher.replaceFirst(" ");
            } catch (Exception ignored) {}
        }

        // 4. Detect "Under / Max X" price
        Matcher maxMatcher = MAX_PRICE_PATTERN.matcher(workingQuery);
        if (maxMatcher.find()) {
            try {
                BigDecimal parsedMax = parseNumericPrice(maxMatcher.group(1), maxMatcher.group(2));
                if (parsedMax != null) {
                    if (maxPrice != null && !maxPrice.equals(parsedMax)) {
                        hasConflicts = true;
                        conflictDescription = "Multiple conflicting max price constraints: " + maxPrice + " vs " + parsedMax;
                        notes.add("Conflict detected in max price constraints");
                    } else {
                        maxPrice = parsedMax;
                        notes.add("Maximum price: " + maxPrice);
                    }
                }
                workingQuery = maxMatcher.replaceFirst(" ");
            } catch (Exception ignored) {}
        }

        // 5. Detect "Above / Min X" price
        Matcher minMatcher = MIN_PRICE_PATTERN.matcher(workingQuery);
        if (minMatcher.find()) {
            try {
                BigDecimal parsedMin = parseNumericPrice(minMatcher.group(1), minMatcher.group(2));
                if (parsedMin != null) {
                    if (minPrice != null && !minPrice.equals(parsedMin)) {
                        hasConflicts = true;
                        conflictDescription = "Multiple conflicting min price constraints: " + minPrice + " vs " + parsedMin;
                        notes.add("Conflict detected in min price constraints");
                    } else {
                        minPrice = parsedMin;
                        notes.add("Minimum price: " + minPrice);
                    }
                }
                workingQuery = minMatcher.replaceFirst(" ");
            } catch (Exception ignored) {}
        }

        // Validate cross-constraint price logic (minPrice vs maxPrice)
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            hasConflicts = true;
            conflictDescription = "Conflicting price constraints: minPrice (" + minPrice + ") exceeds maxPrice (" + maxPrice + ")";
            notes.add("Price conflict: minPrice > maxPrice");
        }

        // 6. Detect Availability
        Matcher stockMatcher = IN_STOCK_PATTERN.matcher(workingQuery);
        if (stockMatcher.find()) {
            inStock = true;
            notes.add("In-stock items only");
            workingQuery = stockMatcher.replaceFirst(" ");
        }

        // 7. Detect Sort Intent
        if (CHEAPEST_SORT_PATTERN.matcher(workingQuery).find()) {
            sortIntent = "price-asc";
            dealIntent = true;
            notes.add("Sort intent: lowest price first");
            workingQuery = CHEAPEST_SORT_PATTERN.matcher(workingQuery).replaceAll(" ");
        } else if (EXPENSIVE_SORT_PATTERN.matcher(workingQuery).find()) {
            sortIntent = "price-desc";
            notes.add("Sort intent: highest price first");
            workingQuery = EXPENSIVE_SORT_PATTERN.matcher(workingQuery).replaceAll(" ");
        } else if (DISCOUNT_SORT_PATTERN.matcher(workingQuery).find()) {
            sortIntent = "discount-desc";
            dealIntent = true;
            notes.add("Sort intent: highest discount first");
            workingQuery = DISCOUNT_SORT_PATTERN.matcher(workingQuery).replaceAll(" ");
        } else if (NEWEST_SORT_PATTERN.matcher(workingQuery).find()) {
            sortIntent = "newest";
            notes.add("Sort intent: newest first");
            workingQuery = NEWEST_SORT_PATTERN.matcher(workingQuery).replaceAll(" ");
        }

        // 8. Detect General Deal Intent
        if (DEAL_PATTERN.matcher(workingQuery).find()) {
            dealIntent = true;
            notes.add("Deal/Value intent detected");
            workingQuery = DEAL_PATTERN.matcher(workingQuery).replaceAll(" ");
        }

        // 9. Detect Brand
        for (Map.Entry<String, String> entry : KNOWN_BRANDS.entrySet()) {
            Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher bm = brandPattern.matcher(workingQuery);
            if (bm.find()) {
                detectedBrand = entry.getValue();
                detectedAttributes.put("brand", detectedBrand);
                notes.add("Detected brand: " + detectedBrand);
                break;
            }
        }

        // 10. Detect Category
        for (Map.Entry<String, String> entry : CATEGORY_ALIASES.entrySet()) {
            Pattern catPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher cm = catPattern.matcher(workingQuery);
            if (cm.find()) {
                detectedCategory = entry.getValue();
                detectedAttributes.put("category", detectedCategory);
                notes.add("Detected category: " + detectedCategory);
                break;
            }
        }

        // 11. Extract Clean Semantic Query Text
        String semanticQuery = extractCleanSemanticQuery(workingQuery);

        return ShoppingQueryIntent.builder()
                .rawQuery(rawQuery)
                .semanticQuery(semanticQuery)
                .category(detectedCategory)
                .brand(detectedBrand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStock(inStock)
                .sortIntent(sortIntent)
                .dealIntent(dealIntent)
                .hasConflicts(hasConflicts)
                .conflictDescription(conflictDescription)
                .confidenceNotes(notes)
                .detectedAttributes(detectedAttributes)
                .build();
    }

    private BigDecimal parseNumericPrice(String numberStr, String multiplier) {
        if (numberStr == null || numberStr.isBlank()) {
            return null;
        }
        try {
            String clean = numberStr.replace(",", "").trim();
            BigDecimal val = new BigDecimal(clean);
            if (multiplier != null && !multiplier.isBlank()) {
                String m = multiplier.trim().toLowerCase();
                if ("k".equals(m) || "thousand".equals(m)) {
                    val = val.multiply(BigDecimal.valueOf(1000));
                } else if ("lakh".equals(m) || "lac".equals(m)) {
                    val = val.multiply(BigDecimal.valueOf(100000));
                }
            }
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCleanSemanticQuery(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Standardize punctuation and collapse spaces
        String cleaned = text.replaceAll("[!?,;:\\\"'()\\[\\]{}<>~`]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Strip leading/trailing residual stopwords like 'with', 'and', 'for', 'in', 'under', 'at'
        String[] words = cleaned.split("\\s+");
        List<String> preservedWords = new ArrayList<>();
        for (String w : words) {
            String lw = w.toLowerCase();
            // Don't strip content adjectives or feature words (ANC, noise cancelling, lightweight, bluetooth)
            if (!lw.isBlank() && !isResidualBoundaryWord(lw)) {
                preservedWords.add(w);
            }
        }

        String result = String.join(" ", preservedWords).trim();
        // If everything got removed (e.g. only structured filters), return clean normalized version of text
        if (result.isBlank()) {
            return cleaned.trim();
        }
        return result;
    }

    private static boolean isResidualBoundaryWord(String word) {
        return Set.of("under", "below", "above", "over", "between", "from", "to", "and", "or", "with", "for", "in", "at", "by", "of", "a", "an", "the")
                .contains(word);
    }
}
