package com.pricepilot.savedproduct;

import com.pricepilot.exception.DuplicateSaveException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.BestPriceProjection;
import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.analytics.ProductAnalyticsService;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.interaction.InteractionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SavedProductService {

    private final SavedProductRepository savedProductRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductAnalyticsService productAnalyticsService;

    private final UserInteractionEventService eventService;

    public SavedProductService(
            SavedProductRepository savedProductRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductPriceRepository productPriceRepository,
            ProductAnalyticsService productAnalyticsService,
            UserInteractionEventService eventService) {
        this.savedProductRepository = savedProductRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productPriceRepository = productPriceRepository;
        this.productAnalyticsService = productAnalyticsService;
        this.eventService = eventService;
    }

    @Transactional
    public void saveProduct(String email, UUID productId) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (product.isArchived()) {
            throw new IllegalArgumentException("Cannot save an archived product");
        }

        SavedProductId savedProductId = new SavedProductId(user.getId(), product.getId());
        if (savedProductRepository.existsById(savedProductId)) {
            throw new DuplicateSaveException("Product is already saved by this user");
        }

        SavedProductEntity savedProduct = SavedProductEntity.builder()
                .id(savedProductId)
                .user(user)
                .product(product)
                .build();

        savedProductRepository.save(savedProduct);
        productAnalyticsService.incrementSaveCount(productId);

        eventService.trackEvent(
                email,
                productId,
                null,
                InteractionType.PRODUCT_SAVE,
                Map.of(
                        "productName", product.getName(),
                        "brand", product.getBrand() != null ? product.getBrand() : "",
                        "category", product.getCategory()
                )
        );
    }

    @Transactional
    public void removeProduct(String email, UUID productId) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        SavedProductId savedProductId = new SavedProductId(user.getId(), productId);
        if (!savedProductRepository.existsById(savedProductId)) {
            throw new ResourceNotFoundException("Saved product association not found for this user");
        }

        savedProductRepository.deleteById(savedProductId);
        productAnalyticsService.decrementSaveCount(productId);

        eventService.trackEvent(
                email,
                productId,
                null,
                InteractionType.PRODUCT_UNSAVE,
                Map.of()
        );
    }

    @Transactional(readOnly = true)
    public List<SavedProductResponseDTO> getSavedProducts(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        List<SavedProductEntity> savedEntities = savedProductRepository.findAllByUserIdWithProduct(user.getId());
        if (savedEntities.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all product IDs to avoid N+1 queries for prices
        List<UUID> productIds = savedEntities.stream()
                .map(sp -> sp.getProduct().getId())
                .collect(Collectors.toList());

        List<BestPriceProjection> prices = productPriceRepository.findBestPricesByProductIds(productIds);

        // Map best prices by product id
        Map<UUID, BigDecimal> bestPricesByProductId = prices.stream()
                .collect(Collectors.toMap(
                        BestPriceProjection::getProductId,
                        BestPriceProjection::getBestPrice
                ));

        return savedEntities.stream().map(sp -> {
            ProductEntity product = sp.getProduct();
            BigDecimal bestPrice = bestPricesByProductId.get(product.getId());

            return SavedProductResponseDTO.builder()
                    .productId(product.getId())
                    .name(product.getName())
                    .brand(product.getBrand())
                    .category(product.getCategory())
                    .imageUrl(product.getImageUrl())
                    .bestPrice(bestPrice)
                    .savedAt(sp.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
