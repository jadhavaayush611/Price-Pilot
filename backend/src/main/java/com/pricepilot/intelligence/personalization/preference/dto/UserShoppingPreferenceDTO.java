package com.pricepilot.intelligence.personalization.preference.dto;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserShoppingPreferenceDTO {
    private UUID id;
    private UUID userId;
    @Builder.Default
    private Set<String> preferredCategories = new HashSet<>();
    @Builder.Default
    private Set<String> preferredBrands = new HashSet<>();
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Double minRating;
    @Builder.Default
    private DealSensitivity dealSensitivity = DealSensitivity.MEDIUM;
    @Builder.Default
    private PriceSensitivity priceSensitivity = PriceSensitivity.MEDIUM;
    @Builder.Default
    private AvailabilityPreference availabilityPreference = AvailabilityPreference.ALL;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
