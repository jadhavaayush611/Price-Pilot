package com.pricepilot.intelligence.assistant.intent;

import com.pricepilot.intelligence.assistant.dto.AssistantEvidenceBundle;
import com.pricepilot.intelligence.assistant.dto.GroundedEvidenceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptInjectionProtector {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionProtector.class);

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (?:all )?previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:forget|ignore) (?:all )?(?:your )?guidelines", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (?:all )?prior prompts", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override system (?:prompt|role)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:in )?developer mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("assistant\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as (?:a )?(?:dan|jailbreak|unrestricted)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend you are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal (?:the )?(?:secret|system) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script.*?>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("drop table", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern PRICE_REGEX = Pattern.compile("\\$\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?|[0-9]+(?:\\.[0-9]{2})?)");
    private static final Pattern DISCOUNT_REGEX = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%\\s*(?:off|discount)");

    public boolean containsInjectionAttempt(String text) {
        if (text == null) return false;
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public String sanitizeUserInput(String text) {
        if (text == null) return "";
        String sanitized = text.trim();

        // Check and log injection attempts
        if (containsInjectionAttempt(sanitized)) {
            log.warn("Detected potential prompt injection in user input. Neutralizing input.");
            for (Pattern pattern : INJECTION_PATTERNS) {
                sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_SECURITY_POLICY]");
            }
        }

        // Strip dangerous formatting / control characters
        sanitized = sanitized.replace("\u0000", "")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return sanitized;
    }

    public String wrapInSafeBoundary(String sanitizedInput) {
        return "<user_query>\n" + sanitizedInput + "\n</user_query>";
    }

    public boolean isResponseGrounded(String responseText, AssistantEvidenceBundle bundle) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return false;
        }

        // If response is excessively short or an obvious error, reject
        if (responseText.trim().length() < 10) {
            return false;
        }

        if (bundle == null) {
            return true;
        }

        // Collect known prices and discounts from evidence bundle
        Set<Double> knownPrices = new HashSet<>();
        Set<Double> knownDiscounts = new HashSet<>();

        if (bundle.getGroundedProducts() != null) {
            for (Map<String, Object> prod : bundle.getGroundedProducts()) {
                extractDouble(prod.get("currentPrice")).ifPresent(knownPrices::add);
                extractDouble(prod.get("price")).ifPresent(knownPrices::add);
                extractDouble(prod.get("originalPrice")).ifPresent(knownPrices::add);
                extractDouble(prod.get("discountPercentage")).ifPresent(knownDiscounts::add);
                extractDouble(prod.get("discount")).ifPresent(knownDiscounts::add);
            }
        }

        if (bundle.getFactualEvidence() != null) {
            for (GroundedEvidenceItem item : bundle.getFactualEvidence()) {
                if ("PRICE".equalsIgnoreCase(item.getFactType())) {
                    extractDouble(item.getFactualValue()).ifPresent(knownPrices::add);
                    extractDouble(item.getBenchmarkValue()).ifPresent(knownPrices::add);
                } else if ("DISCOUNT".equalsIgnoreCase(item.getFactType())) {
                    extractDouble(item.getFactualValue()).ifPresent(knownDiscounts::add);
                }
            }
        }

        // If we have known prices in the evidence bundle, verify price claims
        if (!knownPrices.isEmpty()) {
            String lower = responseText.toLowerCase(Locale.ROOT);
            if ((lower.contains("free") || lower.contains("$0")) && !knownPrices.contains(0.0)) {
                log.warn("Response ungrounded: claims item is free or $0 when known prices are {}", knownPrices);
                return false;
            }

            Matcher priceMatcher = PRICE_REGEX.matcher(responseText);
            while (priceMatcher.find()) {
                try {
                    double claimedPrice = Double.parseDouble(priceMatcher.group(1).replace(",", ""));
                    boolean matchesKnown = false;
                    for (Double known : knownPrices) {
                        if (Math.abs(known - claimedPrice) <= Math.max(1.0, known * 0.05)) {
                            matchesKnown = true;
                            break;
                        }
                    }
                    if (!matchesKnown) {
                        log.warn("Response ungrounded: claimed price ${} does not match any known price in bundle {}",
                                claimedPrice, knownPrices);
                        return false;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // If we have known discounts, verify discount claims
        if (!knownDiscounts.isEmpty()) {
            Matcher discountMatcher = DISCOUNT_REGEX.matcher(responseText);
            while (discountMatcher.find()) {
                try {
                    double claimedDiscount = Double.parseDouble(discountMatcher.group(1));
                    boolean matchesKnown = false;
                    for (Double known : knownDiscounts) {
                        if (Math.abs(known - claimedDiscount) <= 5.0) {
                            matchesKnown = true;
                            break;
                        }
                    }
                    if (!matchesKnown) {
                        log.warn("Response ungrounded: claimed discount {}% does not match known discounts {}",
                                claimedDiscount, knownDiscounts);
                        return false;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return true;
    }

    private Optional<Double> extractDouble(Object obj) {
        if (obj == null) return Optional.empty();
        if (obj instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        try {
            return Optional.of(Double.parseDouble(obj.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
