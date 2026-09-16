package com.pricepilot.intelligence.personalization.context;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable domain-level representation of the personalization context for a single user/request.
 * Fully separated from JPA persistence, HTTP sessions, and recommendation scoring.
 * <p>
 * Core Architectural Principle:
 * "Personalization changes ranking and selection among objectively valid candidates.
 * It does not change product facts or override hard constraints."
 */
public final class PersonalizationContext implements Serializable {

    private final UUID userId;
    private final List<PersonalizationSignal> signals;

    // Derived lazy/immutable views for fast domain querying
    private final Set<String> preferredCategories;
    private final Set<String> preferredBrands;
    private final BigDecimal minBudget;
    private final BigDecimal maxBudget;
    private final Double minRating;
    private final DealSensitivity dealSensitivity;
    private final PriceSensitivity priceSensitivity;
    private final AvailabilityPreference availabilityPreference;
    private final Map<String, Double> categoryAffinities;
    private final Map<String, Double> brandAffinities;
    private final Set<UUID> interactedProductIds;

    public PersonalizationContext(UUID userId, List<PersonalizationSignal> signals) {
        this.userId = userId;

        if (signals == null || signals.isEmpty()) {
            this.signals = List.of();
            this.preferredCategories = Set.of();
            this.preferredBrands = Set.of();
            this.minBudget = null;
            this.maxBudget = null;
            this.minRating = null;
            this.dealSensitivity = null;
            this.priceSensitivity = null;
            this.availabilityPreference = null;
            this.categoryAffinities = Map.of();
            this.brandAffinities = Map.of();
            this.interactedProductIds = Set.of();
            return;
        }

        // Enforce strict deterministic sorting and defensive copying
        List<PersonalizationSignal> sortedSignals = new ArrayList<>(signals);
        Collections.sort(sortedSignals);
        this.signals = List.copyOf(sortedSignals);

        // Derive explicit preference fields
        Set<String> categories = new LinkedHashSet<>();
        Set<String> brands = new LinkedHashSet<>();
        BigDecimal resolvedMinBudget = null;
        BigDecimal resolvedMaxBudget = null;
        Double resolvedMinRating = null;
        DealSensitivity resolvedDealSensitivity = null;
        PriceSensitivity resolvedPriceSensitivity = null;
        AvailabilityPreference resolvedAvailabilityPreference = null;

        Map<String, Double> catAffinities = new LinkedHashMap<>();
        Map<String, Double> brAffinities = new LinkedHashMap<>();
        Set<UUID> resolvedInteractedProductIds = new LinkedHashSet<>();

        for (PersonalizationSignal sig : this.signals) {
            if (sig.source() == PersonalizationSource.EXPLICIT_PREFERENCE) {
                switch (sig.signalType()) {
                    case CATEGORY_PREFERENCE -> {
                        if (sig.targetKey() != null) {
                            categories.add(sig.targetKey());
                        } else if (sig.normalizedValue() != null) {
                            categories.add(sig.normalizedValue().toLowerCase());
                        }
                    }
                    case BRAND_PREFERENCE -> {
                        if (sig.targetKey() != null) {
                            brands.add(sig.targetKey());
                        } else if (sig.normalizedValue() != null) {
                            brands.add(sig.normalizedValue().toLowerCase());
                        }
                    }
                    case BUDGET_PREFERENCE -> {
                        String val = sig.normalizedValue();
                        if (val.contains(":")) {
                            String[] parts = val.split(":", 2);
                            if (!parts[0].isEmpty() && !"null".equalsIgnoreCase(parts[0])) {
                                resolvedMinBudget = parseBigDecimal(parts[0]);
                            }
                            if (parts.length > 1 && !parts[1].isEmpty() && !"null".equalsIgnoreCase(parts[1])) {
                                resolvedMaxBudget = parseBigDecimal(parts[1]);
                            }
                        } else if (val.startsWith("min:")) {
                            resolvedMinBudget = parseBigDecimal(val.substring(4));
                        } else if (val.startsWith("max:")) {
                            resolvedMaxBudget = parseBigDecimal(val.substring(4));
                        }
                    }
                    case RATING_PREFERENCE -> resolvedMinRating = parseDouble(sig.normalizedValue());
                    case DEAL_SENSITIVITY -> resolvedDealSensitivity = DealSensitivity.fromString(sig.normalizedValue());
                    case PRICE_SENSITIVITY -> resolvedPriceSensitivity = PriceSensitivity.fromString(sig.normalizedValue());
                    case AVAILABILITY_PREFERENCE -> resolvedAvailabilityPreference = AvailabilityPreference.fromString(sig.normalizedValue());
                    default -> {}
                }
            } else if (sig.source() == PersonalizationSource.BEHAVIORAL_SIGNAL) {
                switch (sig.signalType()) {
                    case CATEGORY_AFFINITY -> {
                        String key = sig.targetKey() != null ? sig.targetKey() : sig.normalizedValue().toLowerCase();
                        catAffinities.put(key, sig.strength().value());
                    }
                    case BRAND_AFFINITY -> {
                        String key = sig.targetKey() != null ? sig.targetKey() : sig.normalizedValue().toLowerCase();
                        brAffinities.put(key, sig.strength().value());
                    }
                    case INTERACTION_AFFINITY -> {
                        String target = sig.targetKey() != null ? sig.targetKey() : sig.normalizedValue();
                        if (target != null && !target.trim().isEmpty()) {
                            try {
                                resolvedInteractedProductIds.add(UUID.fromString(target.trim()));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    default -> {}
                }
            }
        }

        this.preferredCategories = Set.copyOf(categories);
        this.preferredBrands = Set.copyOf(brands);
        this.minBudget = resolvedMinBudget;
        this.maxBudget = resolvedMaxBudget;
        this.minRating = resolvedMinRating;
        this.dealSensitivity = resolvedDealSensitivity;
        this.priceSensitivity = resolvedPriceSensitivity;
        this.availabilityPreference = resolvedAvailabilityPreference;
        this.categoryAffinities = Map.copyOf(catAffinities);
        this.brandAffinities = Map.copyOf(brAffinities);
        this.interactedProductIds = Set.copyOf(resolvedInteractedProductIds);
    }

    public UUID getUserId() {
        return userId;
    }

    public List<PersonalizationSignal> getSignals() {
        return signals;
    }

    public List<PersonalizationSignal> getSignalsBySource(PersonalizationSource source) {
        if (source == null) {
            return List.of();
        }
        return signals.stream()
                .filter(s -> s.source() == source)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<PersonalizationSignal> getSignalsByType(PersonalizationSignalType type) {
        if (type == null) {
            return List.of();
        }
        return signals.stream()
                .filter(s -> s.signalType() == type)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<PersonalizationSignal> getExplicitSignals() {
        return getSignalsBySource(PersonalizationSource.EXPLICIT_PREFERENCE);
    }

    public List<PersonalizationSignal> getBehavioralSignals() {
        return getSignalsBySource(PersonalizationSource.BEHAVIORAL_SIGNAL);
    }

    public Set<String> getPreferredCategories() {
        return preferredCategories;
    }

    public Set<String> getPreferredBrands() {
        return preferredBrands;
    }

    public Optional<BigDecimal> getMinBudget() {
        return Optional.ofNullable(minBudget);
    }

    public Optional<BigDecimal> getMaxBudget() {
        return Optional.ofNullable(maxBudget);
    }

    public Optional<Double> getMinRating() {
        return Optional.ofNullable(minRating);
    }

    public Optional<DealSensitivity> getDealSensitivity() {
        return Optional.ofNullable(dealSensitivity);
    }

    public Optional<PriceSensitivity> getPriceSensitivity() {
        return Optional.ofNullable(priceSensitivity);
    }

    public Optional<AvailabilityPreference> getAvailabilityPreference() {
        return Optional.ofNullable(availabilityPreference);
    }

    public double getCategoryAffinity(String category) {
        if (category == null) {
            return 0.0;
        }
        return categoryAffinities.getOrDefault(category.trim().toLowerCase(), 0.0);
    }

    public double getBrandAffinity(String brand) {
        if (brand == null) {
            return 0.0;
        }
        return brandAffinities.getOrDefault(brand.trim().toLowerCase(), 0.0);
    }

    public Map<String, Double> getCategoryAffinities() {
        return categoryAffinities;
    }

    public Map<String, Double> getBrandAffinities() {
        return brandAffinities;
    }

    public boolean hasInteractedWithProduct(UUID productId) {
        if (productId == null) {
            return false;
        }
        return interactedProductIds.contains(productId);
    }

    public double getProductInteractionAffinity(UUID productId) {
        if (productId == null) {
            return 0.0;
        }
        return interactedProductIds.contains(productId) ? 1.0 : 0.0;
    }

    public Set<UUID> getInteractedProductIds() {
        return interactedProductIds;
    }

    public boolean isEmpty() {
        return signals.isEmpty();
    }

    public boolean hasSignals() {
        return !signals.isEmpty();
    }

    public int signalCount() {
        return signals.size();
    }

    public static PersonalizationContext empty(UUID userId) {
        return new PersonalizationContext(userId, Collections.emptyList());
    }

    public static PersonalizationContextBuilder builder(UUID userId) {
        return new PersonalizationContextBuilder(userId);
    }

    private static BigDecimal parseBigDecimal(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalizationContext that)) return false;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(signals, that.signals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, signals);
    }

    @Override
    public String toString() {
        return "PersonalizationContext{" +
                "userId=" + userId +
                ", signalCount=" + signals.size() +
                ", explicitCount=" + getExplicitSignals().size() +
                ", behavioralCount=" + getBehavioralSignals().size() +
                '}';
    }
}
