package com.pricepilot.productprice.dto;

import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import com.pricepilot.productprice.ProductPriceEntity;
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
public class ProductPriceResponseDTO {

    private UUID id;
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    private String productUrl;
    private LocalDateTime lastUpdated;
    private ProductResponseDTO product;
    private SellerResponseDTO seller;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductPriceResponseDTO fromEntity(ProductPriceEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductPriceResponseDTO.builder()
                .id(entity.getId())
                .currentPrice(entity.getCurrentPrice())
                .originalPrice(entity.getOriginalPrice())
                .discountPercentage(entity.getDiscountPercentage())
                .productUrl(entity.getProductUrl())
                .lastUpdated(entity.getLastUpdated())
                .product(ProductResponseDTO.fromEntity(entity.getProduct()))
                .seller(SellerResponseDTO.fromEntity(entity.getSeller()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
