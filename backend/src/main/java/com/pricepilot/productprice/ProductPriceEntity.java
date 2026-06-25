package com.pricepilot.productprice;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.exception.InvalidPriceException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_prices", indexes = {
    @Index(name = "idx_product_prices_product_id", columnList = "product_id"),
    @Index(name = "idx_product_prices_seller_id", columnList = "seller_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceEntity extends BaseEntity {

    @Column(name = "current_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "product_url", columnDefinition = "TEXT")
    private String productUrl;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerEntity seller;

    @PrePersist
    @PreUpdate
    public void calculateDiscountAndTimestamps() {
        this.lastUpdated = LocalDateTime.now();

        if (originalPrice == null || currentPrice == null) {
            throw new InvalidPriceException("Original price and current price must not be null");
        }

        if (originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPriceException("Original price must be greater than zero");
        }

        if (currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidPriceException("Current price cannot be negative");
        }

        if (currentPrice.compareTo(originalPrice) > 0) {
            throw new InvalidPriceException("Current price cannot be greater than original price (invalid discount)");
        }

        // Formula: ((originalPrice - currentPrice) / originalPrice) * 100
        BigDecimal diff = originalPrice.subtract(currentPrice);
        this.discountPercentage = diff.divide(originalPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
