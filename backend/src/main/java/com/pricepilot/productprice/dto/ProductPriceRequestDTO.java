package com.pricepilot.productprice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceRequestDTO {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Seller ID is required")
    private UUID sellerId;

    @NotNull(message = "Current price is required")
    @DecimalMin(value = "0.0", message = "Current price cannot be negative")
    private BigDecimal currentPrice;

    @NotNull(message = "Original price is required")
    @DecimalMin(value = "0.01", message = "Original price must be greater than zero")
    private BigDecimal originalPrice;

    private String productUrl;
}
