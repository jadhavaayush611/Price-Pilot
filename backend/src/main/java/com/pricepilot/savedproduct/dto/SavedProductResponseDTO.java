package com.pricepilot.savedproduct.dto;

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
public class SavedProductResponseDTO {
    private UUID productId;
    private String name;
    private String brand;
    private String category;
    private String imageUrl;
    private BigDecimal bestPrice;
    private LocalDateTime savedAt;
}
