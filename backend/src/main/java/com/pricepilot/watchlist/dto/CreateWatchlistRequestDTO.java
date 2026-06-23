package com.pricepilot.watchlist.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWatchlistRequestDTO {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Target price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Target price must be greater than zero")
    private BigDecimal targetPrice;
}
