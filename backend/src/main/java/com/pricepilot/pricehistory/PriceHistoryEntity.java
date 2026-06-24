package com.pricepilot.pricehistory;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.seller.SellerEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_histories", indexes = {
    @Index(name = "idx_price_histories_product_id", columnList = "product_id"),
    @Index(name = "idx_price_histories_seller_id", columnList = "seller_id"),
    @Index(name = "idx_price_histories_changed_at", columnList = "changed_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistoryEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerEntity seller;

    @Column(name = "old_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "price_difference", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDifference;

    @Column(name = "change_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal changePercentage;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
