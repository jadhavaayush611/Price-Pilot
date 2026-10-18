package com.pricepilot.intelligence.marketplace.adapter;

import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter that reads existing persisted catalog offers from the database.
 * Preserves catalog provenance as DEMO / UNVERIFIED and does not claim to be live.
 */
@Component
public class CatalogOfferAdapter implements MarketplaceOfferAdapter {

    public static final String PROVIDER_ID = "catalog";
    public static final String DISPLAY_NAME = "Local Product Catalog";

    private final ProductPriceRepository productPriceRepository;

    public CatalogOfferAdapter(ProductPriceRepository productPriceRepository) {
        this.productPriceRepository = productPriceRepository;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
        if (context == null || context.productId() == null) {
            return Collections.emptyList();
        }

        List<ProductPriceEntity> entities = productPriceRepository.findPricesWithSellersByProductIds(List.of(context.productId()));
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<RawMarketplaceOffer> rawOffers = new ArrayList<>();
        for (ProductPriceEntity entity : entities) {
            if (entity == null) continue;

            String sellerName = entity.getSeller() != null ? entity.getSeller().getName() : "Catalog Merchant";
            String sellerUrl = entity.getSeller() != null ? entity.getSeller().getWebsiteUrl() : null;
            String offerUrl = entity.getProductUrl() != null ? entity.getProductUrl() : sellerUrl;
            LocalDateTime lastUpdated = entity.getLastUpdated() != null ? entity.getLastUpdated() : entity.getUpdatedAt();

            rawOffers.add(new RawMarketplaceOffer(
                    entity.getId() != null ? entity.getId().toString() : null,
                    sellerName,
                    sellerUrl,
                    offerUrl,
                    entity.getCurrentPrice(),
                    entity.getOriginalPrice(),
                    "USD",
                    null, // Unknown stock status for catalog baseline
                    null,
                    lastUpdated
            ));
        }

        return rawOffers;
    }
}
