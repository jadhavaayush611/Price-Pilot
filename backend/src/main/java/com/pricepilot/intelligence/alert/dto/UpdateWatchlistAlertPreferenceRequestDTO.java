package com.pricepilot.intelligence.alert.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWatchlistAlertPreferenceRequestDTO {

    private Boolean enabled;

    private Boolean priceDropEnabled;

    @DecimalMin(value = "1.00", message = "Price drop percentage must be at least 1%")
    @DecimalMax(value = "90.00", message = "Price drop percentage must not exceed 90%")
    @Digits(integer = 2, fraction = 2, message = "Price drop percentage must have valid format")
    private BigDecimal priceDropPercentage;

    private Boolean targetPriceEnabled;

    private Boolean historicalLowEnabled;

    private Boolean goodDealEnabled;

    private Boolean backInStockEnabled;

    private Boolean priceIncreaseEnabled;
}
