package com.pricepilot.intelligence.personalization.signals;

import com.pricepilot.interaction.InteractionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserShoppingSignals {

    private UUID userId;

    @Builder.Default
    private Map<String, Double> categoryAffinity = new HashMap<>();

    @Builder.Default
    private Map<String, Double> brandAffinity = new HashMap<>();

    @Builder.Default
    private Set<UUID> recentInteractedProductIds = new HashSet<>();

    @Builder.Default
    private Map<InteractionType, Integer> interactionCountsByType = new HashMap<>();

    @Builder.Default
    private int totalInteractions = 0;

    public double getCategoryAffinity(String category) {
        if (category == null || categoryAffinity == null) return 0.0;
        return categoryAffinity.getOrDefault(category.toLowerCase().trim(), 0.0);
    }

    public double getBrandAffinity(String brand) {
        if (brand == null || brandAffinity == null) return 0.0;
        return brandAffinity.getOrDefault(brand.toLowerCase().trim(), 0.0);
    }

    public static UserShoppingSignals neutral() {
        return UserShoppingSignals.builder().build();
    }
}
