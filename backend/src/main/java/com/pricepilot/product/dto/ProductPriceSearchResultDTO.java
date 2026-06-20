package com.pricepilot.product.dto;

import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.seller.dto.SellerResponseDTO;
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
public class ProductPriceSearchResultDTO {

    private UUID id;
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    private String productUrl;
    private LocalDateTime lastUpdated;
    private SellerResponseDTO seller;

    public static ProductPriceSearchResultDTO fromEntity(ProductPriceEntity entity) {
        if (entity == null) {
            return null;
        }

        return ProductPriceSearchResultDTO.builder()
                .id(entity.getId())
                .currentPrice(entity.getCurrentPrice())
                .originalPrice(entity.getOriginalPrice())
                .discountPercentage(entity.getDiscountPercentage())
                .productUrl(entity.getProductUrl())
                .lastUpdated(entity.getLastUpdated())
                .seller(SellerResponseDTO.fromEntity(entity.getSeller()))
                .build();
    }
}
