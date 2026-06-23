package com.pricepilot.product.dto;

import com.pricepilot.product.ProductEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResultDTO {

    private UUID id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Builder.Default
    private List<ProductPriceSearchResultDTO> prices = new ArrayList<>();
    
    private BigDecimal lowestPrice;
    private BigDecimal highestPrice;

    public static ProductSearchResultDTO fromEntity(ProductEntity entity) {
        if (entity == null) {
            return null;
        }

        return ProductSearchResultDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .brand(entity.getBrand())
                .category(entity.getCategory())
                .description(entity.getDescription())
                .imageUrl(entity.getImageUrl())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
