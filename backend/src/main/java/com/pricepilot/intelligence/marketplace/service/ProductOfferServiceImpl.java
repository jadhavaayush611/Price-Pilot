package com.pricepilot.intelligence.marketplace.service;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.marketplace.adapter.MarketplaceOfferAggregatorService;
import com.pricepilot.intelligence.marketplace.adapter.MarketplaceQueryContext;
import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of ProductOfferService.
 * Reuses MarketplaceOfferAggregatorService and existing adapters to retrieve
 * normalized offers for a product without modifying legacy DTOs or endpoint contracts.
 */
@Service
public class ProductOfferServiceImpl implements ProductOfferService {

    private final ProductRepository productRepository;
    private final MarketplaceOfferAggregatorService aggregatorService;

    public ProductOfferServiceImpl(ProductRepository productRepository,
                                   MarketplaceOfferAggregatorService aggregatorService) {
        this.productRepository = productRepository;
        this.aggregatorService = aggregatorService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NormalizedOfferDTO> getProductOffers(UUID productId) {
        if (productId == null) {
            return Collections.emptyList();
        }

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        MarketplaceQueryContext context = new MarketplaceQueryContext(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                null,
                10
        );

        return aggregatorService.aggregateOffers(context);
    }
}
