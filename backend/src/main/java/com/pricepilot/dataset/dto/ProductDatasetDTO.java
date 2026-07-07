package com.pricepilot.dataset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDatasetDTO {
    private UUID id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    private BigDecimal currentMinPrice;
    private BigDecimal currentMaxPrice;
    private BigDecimal originalMinPrice;
    private BigDecimal originalMaxPrice;
    private BigDecimal averageDiscountPercentage;
    private int sellerCount;
    private Double averageSellerRating;
}
