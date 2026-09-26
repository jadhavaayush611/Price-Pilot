package com.pricepilot.intelligence.discovery.interpretation;

import com.pricepilot.currency.*;
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
    private final CurrencyConversionService currencyConversionService;

    // Currency prefix or suffix pattern
    private static final String CURR_PREFIX = "(?:(₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp|¥|jpy)\\s*)?";
    private static final String CURR_SUFFIX = "(?:\\s*(₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp|¥|jpy))?";
    private static final String NUM_VAL = "((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*(k|thousand|lakh|lac)?";

    // Price patterns
    private static final Pattern BETWEEN_PRICE_PATTERN =
            Pattern.compile("\\b(?:between|from)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\s+(?:and|to|-)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_PRICE_PATTERN =
            Pattern.compile("\\b(?:under|below|less than|max|up to|budget|within|<=?)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE_PATTERN =
            Pattern.compile("\\b(?:above|over|more than|min|at least|starting from|>=?)\\s+" + CURR_PREFIX + NUM_VAL + CURR_SUFFIX + "\\b", Pattern.CASE_INSENSITIVE);

    // Rating patterns
    private static final Pattern RATING_PATTERN =
            Pattern.compile("\\b(?:above|min|at least)?\\s*([1-5](?:\\.[0-9])?)\\s*(?:\\+|star|stars|rating)\\b", Pattern.CASE_INSENSITIVE);

    // Availability patterns
    private static final Pattern IN_STOCK_PATTERN =
            Pattern.compile("\\b(?:in stock|available)\\b", Pattern.CASE_INSENSITIVE);

    // Deal intent patterns
    private static final Pattern DEAL_PATTERN =
            Pattern.compile("\\b(?:best deal|best value|best|deals|deal|cheap|cheapest|discounted|discount|on sale|sale|low price)\\b", Pattern.CASE_INSENSITIVE);

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

    @org.springframework.beans.factory.annotation.Autowired
    public QueryInterpreter(
            QueryNormalizer queryNormalizer,
            CurrencyConversionService currencyConversionService) {
        this.queryNormalizer = queryNormalizer;
        this.currencyConversionService = currencyConversionService;
    }

    public QueryInterpreter(QueryNormalizer queryNormalizer) {
        this(queryNormalizer, new CurrencyConversionServiceImpl(
                new ConfiguredCurrencyRateProvider(new CurrencyProperties()),
                new CurrencyProperties()
        ));
    }

    /**
     * Interprets a raw user query string into structured parameters with default INR currency.
     */
    public InterpretedQuery interpret(String rawQuery) {
        return interpret(rawQuery, CurrencyCode.INR);
    }

    /**
     * Interprets a raw user query string into structured parameters with user's selected display currency.
     */
    public InterpretedQuery interpret(String rawQuery, CurrencyCode userCurrency) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return InterpretedQuery.builder()
                    .originalQuery("")
                    .normalizedQuery("")
                    .cleanSearchTerms("")
                    .searchTokens(Collections.emptyList())
                    .interpretationNotes(Collections.emptyList())
                    .sourceCurrency(userCurrency != null ? userCurrency : CurrencyCode.INR)
                    .build();
        }

        CurrencyCode effectiveFallbackCurrency = userCurrency != null ? userCurrency : CurrencyCode.INR;

        String normalized = queryNormalizer.normalize(rawQuery);
        String workingQuery = normalized;
        List<String> notes = new ArrayList<>();

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        BigDecimal rawMinPrice = null;
        BigDecimal rawMaxPrice = null;
        CurrencyCode detectedSourceCurrency = effectiveFallbackCurrency;

        Double minRating = null;
        Boolean inStockOnly = null;
        Boolean dealIntent = null;
        String detectedBrand = null;
        String detectedCategory = null;

        // 1. Detect "Between X and Y" price
        Matcher betweenMatcher = BETWEEN_PRICE_PATTERN.matcher(workingQuery);
        if (betweenMatcher.find()) {
            try {
                ParsedPrice p1 = extractPrice(betweenMatcher.group(1), betweenMatcher.group(2), betweenMatcher.group(3), betweenMatcher.group(4), effectiveFallbackCurrency);
                ParsedPrice p2 = extractPrice(betweenMatcher.group(5), betweenMatcher.group(6), betweenMatcher.group(7), betweenMatcher.group(8), effectiveFallbackCurrency);
                if (p1 != null && p2 != null) {
                    minPrice = p1.canonicalAmount();
                    maxPrice = p2.canonicalAmount();
                    rawMinPrice = p1.rawAmount();
                    rawMaxPrice = p2.rawAmount();
                    detectedSourceCurrency = p2.detectedCurrency();
                    notes.add("Price between " + p1.detectedCurrency().getSymbol() + p1.rawAmount() + " and " + p2.detectedCurrency().getSymbol() + p2.rawAmount() + " ($" + minPrice + " to $" + maxPrice + " USD)");
                    workingQuery = betweenMatcher.replaceFirst("");
                }
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
                    ParsedPrice parsedMax = extractPrice(maxMatcher.group(1), maxMatcher.group(2), maxMatcher.group(3), maxMatcher.group(4), effectiveFallbackCurrency);
                    if (parsedMax != null) {
                        maxPrice = parsedMax.canonicalAmount();
                        rawMaxPrice = parsedMax.rawAmount();
                        detectedSourceCurrency = parsedMax.detectedCurrency();
                        notes.add("Maximum price: " + parsedMax.detectedCurrency().getSymbol() + parsedMax.rawAmount() + " ($" + maxPrice + " USD)");
                        workingQuery = maxMatcher.replaceFirst("");
                    }
                } catch (Exception ignored) {}
            }
        }

        // 4. Detect "Above / Min X" price
        if (minPrice == null) {
            Matcher minMatcher = MIN_PRICE_PATTERN.matcher(workingQuery);
            if (minMatcher.find()) {
                try {
                    ParsedPrice parsedMin = extractPrice(minMatcher.group(1), minMatcher.group(2), minMatcher.group(3), minMatcher.group(4), effectiveFallbackCurrency);
                    if (parsedMin != null) {
                        minPrice = parsedMin.canonicalAmount();
                        rawMinPrice = parsedMin.rawAmount();
                        detectedSourceCurrency = parsedMin.detectedCurrency();
                        notes.add("Minimum price: " + parsedMin.detectedCurrency().getSymbol() + parsedMin.rawAmount() + " ($" + minPrice + " USD)");
                        workingQuery = minMatcher.replaceFirst("");
                    }
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
            Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
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
            Pattern catPattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
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
                .rawMinPrice(rawMinPrice)
                .rawMaxPrice(rawMaxPrice)
                .sourceCurrency(detectedSourceCurrency)
                .minRating(minRating)
                .inStockOnly(inStockOnly)
                .dealIntent(dealIntent)
                .interpretationNotes(notes)
                .build();
    }

    private record ParsedPrice(BigDecimal rawAmount, CurrencyCode detectedCurrency, BigDecimal canonicalAmount) {}

    private ParsedPrice extractPrice(String prefix, String numStr, String mult, String suffix, CurrencyCode fallbackCurrency) {
        if (numStr == null || numStr.isBlank()) {
            return null;
        }
        BigDecimal raw = parseNumericPrice(numStr, mult);
        if (raw == null) {
            return null;
        }
        CurrencyCode currency = null;
        if (prefix != null && !prefix.isBlank()) {
            currency = CurrencyCode.fromToken(prefix);
        }
        if (currency == null && suffix != null && !suffix.isBlank()) {
            currency = CurrencyCode.fromToken(suffix);
        }
        if (currency == null) {
            currency = fallbackCurrency != null ? fallbackCurrency : CurrencyCode.INR;
        }
        BigDecimal canonical = currencyConversionService.convertToCanonical(raw, currency);
        return new ParsedPrice(raw, currency, canonical);
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

    private static boolean isStopWord(String word) {
        return Set.of("the", "a", "an", "for", "with", "and", "or", "in", "at", "to", "by", "of", "from")
                .contains(word);
    }
}
