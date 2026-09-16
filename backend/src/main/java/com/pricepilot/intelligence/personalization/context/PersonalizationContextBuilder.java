package com.pricepilot.intelligence.personalization.context;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controlled, deterministic builder for constructing immutable {@link PersonalizationContext} instances.
 * Normalizes input values, validates constraints, eliminates duplicate signals, and orders signals deterministically.
 */
public final class PersonalizationContextBuilder {

    private UUID userId;
    private final Map<String, PersonalizationSignal> signalMap = new LinkedHashMap<>();

    private final Set<String> preferredCategories = new LinkedHashSet<>();
    private final Set<String> preferredBrands = new LinkedHashSet<>();
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Double minRating;
    private DealSensitivity dealSensitivity;
    private PriceSensitivity priceSensitivity;
    private AvailabilityPreference availabilityPreference;

    private final Map<String, Double> categoryAffinities = new LinkedHashMap<>();
    private final Map<String, Double> brandAffinities = new LinkedHashMap<>();

    public PersonalizationContextBuilder() {
    }

    public PersonalizationContextBuilder(UUID userId) {
        this.userId = userId;
    }

    public PersonalizationContextBuilder userId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public PersonalizationContextBuilder addSignal(PersonalizationSignal signal) {
        if (signal == null) {
            return this;
        }
        String dedupKey = buildDedupKey(signal.signalType(), signal.source(), signal.targetKey(), signal.normalizedValue());
        PersonalizationSignal existing = signalMap.get(dedupKey);
        if (existing == null || signal.strength().value() > existing.strength().value()) {
            signalMap.put(dedupKey, signal);
        }
        return this;
    }

    public PersonalizationContextBuilder addSignals(Collection<PersonalizationSignal> signals) {
        if (signals != null) {
            for (PersonalizationSignal signal : signals) {
                addSignal(signal);
            }
        }
        return this;
    }

    public PersonalizationContextBuilder addPreferredCategory(String category) {
        if (category != null && !category.trim().isEmpty()) {
            preferredCategories.add(category.trim());
        }
        return this;
    }

    public PersonalizationContextBuilder addPreferredCategories(Collection<String> categories) {
        if (categories != null) {
            for (String category : categories) {
                addPreferredCategory(category);
            }
        }
        return this;
    }

    public PersonalizationContextBuilder addPreferredBrand(String brand) {
        if (brand != null && !brand.trim().isEmpty()) {
            preferredBrands.add(brand.trim());
        }
        return this;
    }

    public PersonalizationContextBuilder addPreferredBrands(Collection<String> brands) {
        if (brands != null) {
            for (String brand : brands) {
                addPreferredBrand(brand);
            }
        }
        return this;
    }

    public PersonalizationContextBuilder minBudget(BigDecimal minBudget) {
        this.minBudget = minBudget;
        return this;
    }

    public PersonalizationContextBuilder maxBudget(BigDecimal maxBudget) {
        this.maxBudget = maxBudget;
        return this;
    }

    public PersonalizationContextBuilder minRating(Double minRating) {
        if (minRating != null) {
            if (Double.isNaN(minRating) || Double.isInfinite(minRating)) {
                throw new IllegalArgumentException("Min rating cannot be NaN or infinite");
            }
            if (minRating < 0.0 || minRating > 5.0) {
                throw new IllegalArgumentException("Min rating must be between 0.0 and 5.0, got: " + minRating);
            }
            this.minRating = minRating;
        } else {
            this.minRating = null;
        }
        return this;
    }

    public PersonalizationContextBuilder dealSensitivity(DealSensitivity dealSensitivity) {
        this.dealSensitivity = dealSensitivity;
        return this;
    }

    public PersonalizationContextBuilder priceSensitivity(PriceSensitivity priceSensitivity) {
        this.priceSensitivity = priceSensitivity;
        return this;
    }

    public PersonalizationContextBuilder availabilityPreference(AvailabilityPreference availabilityPreference) {
        this.availabilityPreference = availabilityPreference;
        return this;
    }

    public PersonalizationContextBuilder addCategoryAffinity(String category, double affinity) {
        if (category != null && !category.trim().isEmpty()) {
            if (Double.isNaN(affinity) || Double.isInfinite(affinity)) {
                throw new IllegalArgumentException("Category affinity cannot be NaN or infinite");
            }
            double bounded = Math.max(0.0, Math.min(1.0, affinity));
            categoryAffinities.put(category.trim(), bounded);
        }
        return this;
    }

    public PersonalizationContextBuilder addBrandAffinity(String brand, double affinity) {
        if (brand != null && !brand.trim().isEmpty()) {
            if (Double.isNaN(affinity) || Double.isInfinite(affinity)) {
                throw new IllegalArgumentException("Brand affinity cannot be NaN or infinite");
            }
            double bounded = Math.max(0.0, Math.min(1.0, affinity));
            brandAffinities.put(brand.trim(), bounded);
        }
        return this;
    }

    public PersonalizationContext build() {
        // Validate budget range if both are present
        if (minBudget != null && maxBudget != null && minBudget.compareTo(maxBudget) > 0) {
            throw new IllegalArgumentException("Minimum budget (" + minBudget + ") cannot exceed maximum budget (" + maxBudget + ")");
        }

        // Generate explicit signals from builder fields
        for (String cat : preferredCategories) {
            String normalizedCat = cat.trim();
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.CATEGORY_PREFERENCE,
                    normalizedCat.toLowerCase(),
                    normalizedCat
            ));
        }

        for (String brand : preferredBrands) {
            String normalizedBrand = brand.trim();
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.BRAND_PREFERENCE,
                    normalizedBrand.toLowerCase(),
                    normalizedBrand
            ));
        }

        if (minBudget != null || maxBudget != null) {
            String budgetVal = (minBudget != null ? minBudget.toPlainString() : "") + ":" + (maxBudget != null ? maxBudget.toPlainString() : "");
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.BUDGET_PREFERENCE,
                    "budget",
                    budgetVal
            ));
        }

        if (minRating != null) {
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.RATING_PREFERENCE,
                    "min_rating",
                    String.valueOf(minRating)
            ));
        }

        if (dealSensitivity != null) {
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.DEAL_SENSITIVITY,
                    "deal_sensitivity",
                    dealSensitivity.name()
            ));
        }

        if (priceSensitivity != null) {
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.PRICE_SENSITIVITY,
                    "price_sensitivity",
                    priceSensitivity.name()
            ));
        }

        if (availabilityPreference != null) {
            addSignal(PersonalizationSignal.explicit(
                    PersonalizationSignalType.AVAILABILITY_PREFERENCE,
                    "availability_preference",
                    availabilityPreference.name()
            ));
        }

        // Generate behavioral affinity signals from builder fields
        for (Map.Entry<String, Double> entry : categoryAffinities.entrySet()) {
            String cat = entry.getKey().trim();
            addSignal(PersonalizationSignal.behavioral(
                    PersonalizationSignalType.CATEGORY_AFFINITY,
                    cat.toLowerCase(),
                    cat,
                    entry.getValue()
            ));
        }

        for (Map.Entry<String, Double> entry : brandAffinities.entrySet()) {
            String brand = entry.getKey().trim();
            addSignal(PersonalizationSignal.behavioral(
                    PersonalizationSignalType.BRAND_AFFINITY,
                    brand.toLowerCase(),
                    brand,
                    entry.getValue()
            ));
        }

        List<PersonalizationSignal> finalSignals = new ArrayList<>(signalMap.values());
        Collections.sort(finalSignals);

        return new PersonalizationContext(userId, finalSignals);
    }

    private static String buildDedupKey(PersonalizationSignalType type, PersonalizationSource source, String targetKey, String value) {
        return (type != null ? type.name() : "") + "|" +
                (source != null ? source.name() : "") + "|" +
                (targetKey != null ? targetKey.trim().toLowerCase() : "") + "|" +
                (value != null ? value.trim().toLowerCase() : "");
    }
}
