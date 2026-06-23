package com.pricepilot.watchlist;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "price_watchlists", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_price_watchlists_user_product", columnNames = {"user_id", "product_id"})
    }, 
    indexes = {
        @Index(name = "idx_price_watchlists_user_id", columnList = "user_id"),
        @Index(name = "idx_price_watchlists_product_id", columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceWatchlistEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_price_watchlists_user"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_price_watchlists_product"))
    private ProductEntity product;

    @Column(name = "target_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "current_best_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBestPrice;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
