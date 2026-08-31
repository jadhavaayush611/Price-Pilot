package com.pricepilot.intelligence.personalization.preference.dto;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShoppingPreferenceRequest {
    private Set<String> preferredCategories;
    private Set<String> preferredBrands;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum budget must be greater than or equal to 0")
    private BigDecimal minBudget;

    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum budget must be greater than or equal to 0")
    private BigDecimal maxBudget;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum rating must be at least 0.0")
    @DecimalMax(value = "5.0", inclusive = true, message = "Minimum rating cannot exceed 5.0")
    private Double minRating;

    private DealSensitivity dealSensitivity;
    private PriceSensitivity priceSensitivity;
    private AvailabilityPreference availabilityPreference;
}
