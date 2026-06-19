package com.pricepilot.seller;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.productprice.ProductPriceEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sellers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    @OneToMany(mappedBy = "seller", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductPriceEntity> productPrices = new ArrayList<>();
}
