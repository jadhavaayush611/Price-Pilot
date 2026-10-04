package com.pricepilot.intelligence.assistant.matching;

import com.pricepilot.intelligence.assistant.dto.AssistantMatchClassification;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.product.ProductEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Exact Product Match Evaluator.
 *
 * Distinguishes between:
 * 1. Exact catalog match (satisfies the concrete product entity identity)
 * 2. Close / semantic match (nearby candidates, predecessor/successor generations, or model variants)
 * 3. No match (no sufficiently relevant candidate)
 * 4. Category / Discovery results (broad category, budget, or descriptive queries)
 */
@Component
public class ExactProductMatchEvaluator {

    private final QueryNormalizer queryNormalizer;

    private static final Pattern PREFIX_STRIPPER = Pattern.compile(
            "^(?:find(?:\\s+me)?(?:\\s+(?:a|an|the))?|show(?:\\s+me)?(?:\\s+(?:a|an|the))?|where(?:\\s+can\\s+i)?(?:\\s+(?:buy|get|find))(?:\\s+(?:a|an|the))?|where\\s+to\\s+(?:buy|get|find)(?:\\s+(?:a|an|the))?|i\\s+(?:want|need|am\\s+looking\\s+for|look\\s+for)(?:\\s+(?:a|an|the))?|search(?:\\s+for)?(?:\\s+(?:a|an|the))?|looking\\s+for(?:\\s+(?:a|an|the))?|tell\\s+me\\s+about(?:\\s+(?:a|an|the))?|what\\s+about(?:\\s+(?:a|an|the))?|how\\s+much\\s+is(?:\\s+(?:a|an|the))?|price\\s+of(?:\\s+(?:a|an|the))?|get(?:\\s+me)?(?:\\s+(?:a|an|the))?|can\\s+you\\s+find(?:\\s+(?:a|an|the))?)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[?!.]+$");
    private static final Pattern LEADING_ARTICLES = Pattern.compile("^(?:the|a|an)\\s+", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KNOWN_BRANDS = Set.of(
            "apple", "samsung", "sony", "google", "dell", "lenovo", "hp", "asus", "bose", "jbl",
            "audio-technica", "sennheiser", "beats", "oneplus", "xiaomi", "motorola", "nothing",
            "microsoft", "razer", "acer", "shure", "focal", "bowers & wilkins", "amazon", "nintendo",
            "logitech", "lg"
    );

    private static final Set<String> PROPRIETARY_PRODUCT_LINES = Set.of(
            "iphone", "macbook", "ipad", "airpods", "pixel", "galaxy", "thinkpad", "xps",
            "spectre", "zephyrus", "predator", "surface", "blade", "legion", "omen", "zenbook",
            "yoga", "aonic", "quietcomfort", "momentum", "bathys", "tour one", "wh-1000xm",
            "ath-m50x", "ath-m55x", "switch", "playstation", "xbox", "fire hd", "fire max"
    );

    private static final Set<String> GENERIC_CATEGORY_WORDS = Set.of(
            "headphones", "headphone", "earphones", "earphone", "earbuds", "earbud",
            "laptop", "laptops", "notebook", "notebooks", "chromebook",
            "phone", "phones", "smartphone", "smartphones", "mobile",
            "tablet", "tablets", "tv", "monitor", "monitors", "console", "electronics",
            "device", "gadget", "audio"
    );

    private static final List<String> KNOWN_VARIANTS_ORDERED = List.of(
            "pro max", "pro", "max", "plus", "ultra", "mini", "se", "air", "fe", "oled",
            "lite", "ti", "super", "carbon", "fold", "flip", "2a", "8a", "12r", "bt2", "s2e"
    );

    private static final Set<String> COMMON_SPEC_TOKENS = Set.of(
            "4k", "8k", "1080p", "1440p", "720p", "144hz", "120hz", "240hz", "165hz", "60hz",
            "5g", "4g", "3g", "2g", "3d", "2d", "1tb", "2tb", "512gb", "256gb", "128gb", "64gb",
            "32gb", "16gb", "8gb", "4gb", "usb3", "wifi6", "wifi5", "oled", "led"
    );

    public ExactProductMatchEvaluator(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = queryNormalizer != null ? queryNormalizer : new QueryNormalizer();
    }

    /**
     * Extracts the target product entity string from a user query.
     */
    public String extractTargetEntityName(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return "";
        }

        String query = rawQuery.trim();
        query = TRAILING_PUNCTUATION.matcher(query).replaceAll("").trim();

        Matcher prefixMatcher = PREFIX_STRIPPER.matcher(query);
        if (prefixMatcher.find()) {
            query = query.substring(prefixMatcher.end()).trim();
        }

        query = LEADING_ARTICLES.matcher(query).replaceAll("").trim();
        query = TRAILING_PUNCTUATION.matcher(query).replaceAll("").trim();

        return query;
    }

    /**
     * Determines whether the user query expresses an intent for a specific concrete product entity
     * rather than a general category, budget, or descriptive search.
     */
    public boolean isExactProductQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return false;
        }

        String lower = rawQuery.toLowerCase(Locale.ROOT).trim();

        // Comparison or alternative queries are handled by their own respective domains
        if (lower.contains("compare") || lower.contains(" vs ") || lower.contains(" versus ")
                || lower.contains("which is better") || lower.contains("difference between")
                || lower.contains("which one") || lower.contains("alternative")
                || lower.contains("alternatives") || lower.contains("similar to")
                || lower.contains("what is similar") || lower.contains("recommend")
                || lower.contains("suggestion") || lower.contains("what should i")) {
            return false;
        }

        String target = extractTargetEntityName(rawQuery);
        if (target.isEmpty()) {
            return false;
        }

        String lowerTarget = target.toLowerCase(Locale.ROOT);

        // 1. Check if contains proprietary product line name (e.g. "iphone", "pixel", "macbook")
        for (String line : PROPRIETARY_PRODUCT_LINES) {
            if (Pattern.compile("\\b" + Pattern.quote(line) + "\\b").matcher(lowerTarget).find()) {
                return true;
            }
        }

        // 2. Check if contains model numbers or alphanumeric codes (e.g. "WH-1000XM5", "M2", "S24", "16", "15", "ATH-M50xBT2")
        Pattern modelNumberPattern = Pattern.compile("\\b([a-zA-Z]*\\d+[a-zA-Z0-9]*)\\b");
        Matcher m = modelNumberPattern.matcher(lowerTarget);
        boolean hasModelNumber = false;
        while (m.find()) {
            String token = m.group(1).toLowerCase(Locale.ROOT);
            // Ignore pure price or currency tokens like 5000, 80000 if preceded by price keywords
            if (isPriceToken(lower, token)) {
                continue;
            }
            if (COMMON_SPEC_TOKENS.contains(token)) {
                continue;
            }
            hasModelNumber = true;
            break;
        }
        if (hasModelNumber) {
            return true;
        }

        // 3. Check if contains a known brand combined with a non-generic identifier
        for (String brand : KNOWN_BRANDS) {
            if (lowerTarget.contains(brand)) {
                String remaining = lowerTarget.replace(brand, "").trim();
                remaining = LEADING_ARTICLES.matcher(remaining).replaceAll("").trim();
                if (!remaining.isEmpty() && !isOnlyGenericWords(remaining)) {
                    return true;
                }
            }
        }

        // If it's purely generic category + adjectives or budget, it's not an exact product query
        return false;
    }

    private boolean isPriceToken(String fullQuery, String token) {
        Pattern p = Pattern.compile("(?:under|below|less than|budget of|budget|max|up to|₹|\\$|€|£|¥|inr|usd)\\s*" + Pattern.quote(token), Pattern.CASE_INSENSITIVE);
        return p.matcher(fullQuery).find();
    }

    private boolean isOnlyGenericWords(String text) {
        String[] tokens = text.split("\\s+");
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            if (!GENERIC_CATEGORY_WORDS.contains(t)
                    && !Set.of("wireless", "wired", "bluetooth", "gaming", "good", "best", "cheap", "budget", "fast", "pro", "new", "top", "for", "with", "and").contains(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deterministically tests if a candidate product is an exact match for the requested product entity.
     */
    public boolean isExactProductMatch(String targetEntity, String candidateName, String candidateBrand, String candidateCategory) {
        if (targetEntity == null || candidateName == null) {
            return false;
        }

        String normTarget = normalizeText(targetEntity);
        String normCandidate = normalizeText(candidateName);

        // 1. Direct normalized exact match
        if (normTarget.equals(normCandidate)) {
            return true;
        }

        // 2. Brand Check: If target specifies a brand, candidate must match that brand
        String specifiedBrand = extractSpecifiedBrand(normTarget);
        if (specifiedBrand != null) {
            if (candidateBrand != null && !candidateBrand.trim().isEmpty()) {
                String normCandBrand = normalizeText(candidateBrand);
                if (!normCandBrand.contains(specifiedBrand) && !specifiedBrand.contains(normCandBrand)) {
                    return false;
                }
            } else if (!normCandidate.contains(specifiedBrand)) {
                return false;
            }
        }

        // 3. Product Line / Family Check (e.g. "iphone", "pixel", "galaxy")
        String targetFamily = extractProductFamily(normTarget);
        String candidateFamily = extractProductFamily(normCandidate);
        if (targetFamily != null && candidateFamily != null && !targetFamily.equals(candidateFamily)) {
            return false;
        }

        // 4. Model Generation / Number Check (e.g. "16" vs "15", "5" vs "4", "2" vs "1", "xm5" vs "xm4")
        Set<String> targetNumbers = extractDistinctNumbersAndAlphanumericCodes(normTarget);
        Set<String> candidateNumbers = extractDistinctNumbersAndAlphanumericCodes(normCandidate);

        if (!targetNumbers.isEmpty()) {
            if (candidateNumbers.isEmpty() || !candidateNumbers.containsAll(targetNumbers)) {
                return false;
            }
            // Candidate must not have conflicting extra model numbers
            for (String cn : candidateNumbers) {
                if (isGenerationNumber(cn) && !targetNumbers.contains(cn)) {
                    return false;
                }
            }
        }

        // 5. Variant Modifier Check (e.g. "pro", "pro max", "plus", "ultra", "mini", "se")
        Set<String> targetVariants = extractVariants(normTarget);
        Set<String> candidateVariants = extractVariants(normCandidate);

        if (!targetVariants.equals(candidateVariants)) {
            return false;
        }

        // 6. Substring Identity Check without Brand/Category Suffixes
        String cleanTarget = stripBrandAndCategory(normTarget, specifiedBrand);
        String cleanCandidate = stripBrandAndCategory(normCandidate, specifiedBrand != null ? specifiedBrand : (candidateBrand != null ? normalizeText(candidateBrand) : null));

        if (cleanTarget.equals(cleanCandidate)) {
            return true;
        }

        // If candidate name contains clean target as a full token boundary match and shares same model code and variants
        if (Pattern.compile("\\b" + Pattern.quote(cleanTarget) + "\\b").matcher(cleanCandidate).find()) {
            // Verify no conflicting extra model words
            String remainder = cleanCandidate.replace(cleanTarget, "").trim();
            if (remainder.isEmpty() || isHarmlessProductSuffix(remainder)) {
                return true;
            }
        }

        return false;
    }

    private boolean isGenerationNumber(String token) {
        return token.matches("^\\d{1,2}$") || token.matches("^xm\\d$") || token.matches("^m\\d$");
    }

    private boolean isHarmlessProductSuffix(String text) {
        String[] words = text.split("\\s+");
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!GENERIC_CATEGORY_WORDS.contains(w)
                    && !Set.of("wireless", "bluetooth", "noise", "canceling", "cancelling", "headphones", "earbuds", "edition", "gen", "generation", "standard").contains(w)) {
                return false;
            }
        }
        return true;
    }

    private String stripBrandAndCategory(String text, String brand) {
        String res = text;
        if (brand != null && !brand.isEmpty()) {
            res = res.replaceAll("\\b" + Pattern.quote(brand) + "\\b", " ").trim();
        }
        for (String cat : GENERIC_CATEGORY_WORDS) {
            res = res.replaceAll("\\b" + Pattern.quote(cat) + "\\b", " ").trim();
        }
        return res.replaceAll("\\s+", " ").trim();
    }

    public String extractSpecifiedBrand(String text) {
        if (text == null) return null;
        String normalized = normalizeText(text);
        for (String b : KNOWN_BRANDS) {
            if (Pattern.compile("\\b" + Pattern.quote(b) + "\\b").matcher(normalized).find()) {
                return b;
            }
        }
        return null;
    }

    public String extractProductFamily(String text) {
        if (text == null) return null;
        String normalized = normalizeText(text);
        for (String family : PROPRIETARY_PRODUCT_LINES) {
            if (Pattern.compile("\\b" + Pattern.quote(family) + "\\b").matcher(normalized).find()) {
                return family;
            }
        }
        return null;
    }

    private Set<String> extractVariants(String normalizedText) {
        Set<String> found = new LinkedHashSet<>();
        String working = normalizedText;
        for (String variant : KNOWN_VARIANTS_ORDERED) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(variant) + "\\b");
            if (p.matcher(working).find()) {
                found.add(variant);
                working = p.matcher(working).replaceAll(" ");
            }
        }
        return found;
    }

    private Set<String> extractDistinctNumbersAndAlphanumericCodes(String normalizedText) {
        Set<String> codes = new LinkedHashSet<>();
        Pattern p = Pattern.compile("\\b([a-zA-Z]*\\d+[a-zA-Z0-9]*)\\b");
        Matcher m = p.matcher(normalizedText);
        while (m.find()) {
            String code = m.group(1).toLowerCase(Locale.ROOT);
            codes.add(code);
        }
        return codes;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Evaluates candidate discovery products against the query.
     */
    public EvaluationResult evaluateCandidates(
            String query,
            List<DiscoveryProductDTO> candidates,
            List<ProductEntity> allCatalogProducts) {

        if (!isExactProductQuery(query)) {
            if (candidates == null || candidates.isEmpty()) {
                return new EvaluationResult(AssistantMatchClassification.NO_MATCH, null, Collections.emptyList(), Collections.emptyList());
            }
            return new EvaluationResult(AssistantMatchClassification.CATEGORY_RESULTS, null, Collections.emptyList(), candidates);
        }

        String targetEntity = extractTargetEntityName(query);

        List<DiscoveryProductDTO> exactMatches = new ArrayList<>();
        List<DiscoveryProductDTO> closeMatches = new ArrayList<>();

        if (candidates != null) {
            for (DiscoveryProductDTO candidate : candidates) {
                if (isExactProductMatch(targetEntity, candidate.getName(), candidate.getBrand(), candidate.getCategory())) {
                    exactMatches.add(candidate);
                } else {
                    closeMatches.add(candidate);
                }
            }
        }

        if (!exactMatches.isEmpty()) {
            return new EvaluationResult(AssistantMatchClassification.EXACT_MATCH, targetEntity, exactMatches, closeMatches);
        }

        if (!closeMatches.isEmpty()) {
            return new EvaluationResult(AssistantMatchClassification.CLOSE_MATCHES, targetEntity, Collections.emptyList(), closeMatches);
        }

        return new EvaluationResult(AssistantMatchClassification.NO_MATCH, targetEntity, Collections.emptyList(), Collections.emptyList());
    }

    public static class EvaluationResult {
        private final AssistantMatchClassification classification;
        private final String requestedEntity;
        private final List<DiscoveryProductDTO> exactMatches;
        private final List<DiscoveryProductDTO> closeMatches;

        public EvaluationResult(
                AssistantMatchClassification classification,
                String requestedEntity,
                List<DiscoveryProductDTO> exactMatches,
                List<DiscoveryProductDTO> closeMatches) {
            this.classification = classification;
            this.requestedEntity = requestedEntity;
            this.exactMatches = exactMatches != null ? exactMatches : Collections.emptyList();
            this.closeMatches = closeMatches != null ? closeMatches : Collections.emptyList();
        }

        public AssistantMatchClassification getClassification() {
            return classification;
        }

        public String getRequestedEntity() {
            return requestedEntity;
        }

        public List<DiscoveryProductDTO> getExactMatches() {
            return exactMatches;
        }

        public List<DiscoveryProductDTO> getCloseMatches() {
            return closeMatches;
        }
    }
}
