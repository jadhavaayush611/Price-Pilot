package com.pricepilot.recommendation;

import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.BestPriceProjection;
import com.pricepilot.product.ProductEntity;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationProfileServiceImpl implements RecommendationProfileService {

    private final SavedProductRepository savedProductRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final UserInteractionEventRepository eventRepository;
    private final ProductPriceRepository productPriceRepository;

    public RecommendationProfileServiceImpl(
            SavedProductRepository savedProductRepository,
            PriceWatchlistRepository watchlistRepository,
            UserInteractionEventRepository eventRepository,
            ProductPriceRepository productPriceRepository) {
        this.savedProductRepository = savedProductRepository;
        this.watchlistRepository = watchlistRepository;
        this.eventRepository = eventRepository;
        this.productPriceRepository = productPriceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationProfile getProfile(UUID userId) {
        // 1. Fetch saved products
        List<SavedProductEntity> savedProducts = savedProductRepository.findAllByUserIdWithProduct(userId);

        // 2. Fetch watchlists
        List<PriceWatchlistEntity> watchlists = watchlistRepository.findAllByUserIdWithProduct(userId);

        // 3. Fetch recent interaction events (up to 100)
        List<UserInteractionEventEntity> events = eventRepository.findByUserIdWithRelations(userId, PageRequest.of(0, 100));

        // Maps to aggregate weights
        Map<String, Double> categoryWeights = new HashMap<>();
        Map<String, Double> brandWeights = new HashMap<>();
        Map<String, Double> sellerWeights = new HashMap<>();
        Map<String, Long> freqMap = new HashMap<>();

        Set<UUID> productIds = new HashSet<>();

        // Aggregate from Saved Products
        for (SavedProductEntity sp : savedProducts) {
            ProductEntity product = sp.getProduct();
            if (product != null) {
                productIds.add(product.getId());
                incrementWeight(categoryWeights, product.getCategory(), 5.0);
                if (product.getBrand() != null) {
                    incrementWeight(brandWeights, product.getBrand(), 5.0);
                }
            }
        }

        // Aggregate from Watchlists
        for (PriceWatchlistEntity pw : watchlists) {
            ProductEntity product = pw.getProduct();
            if (product != null) {
                productIds.add(product.getId());
                incrementWeight(categoryWeights, product.getCategory(), 10.0);
                if (product.getBrand() != null) {
                    incrementWeight(brandWeights, product.getBrand(), 10.0);
                }
            }
        }

        // Aggregate from Interaction Events
        for (UserInteractionEventEntity event : events) {
            String typeStr = event.getInteractionType().name();
            freqMap.put(typeStr, freqMap.getOrDefault(typeStr, 0L) + 1L);

            ProductEntity product = event.getProduct();
            if (product != null) {
                productIds.add(product.getId());
                double viewWeight = event.getInteractionType() == InteractionType.PRODUCT_VIEW ? 1.0 : 0.0;
                double sellerClickWeight = event.getInteractionType() == InteractionType.SELLER_CLICK ? 2.0 : 0.0;
                double totalEventWeight = viewWeight + sellerClickWeight;

                if (totalEventWeight > 0) {
                    incrementWeight(categoryWeights, product.getCategory(), totalEventWeight);
                    if (product.getBrand() != null) {
                        incrementWeight(brandWeights, product.getBrand(), totalEventWeight);
                    }
                }
            }

            // Sellers clicks
            if (event.getInteractionType() == InteractionType.SELLER_CLICK && event.getSeller() != null) {
                incrementWeight(sellerWeights, event.getSeller().getName(), 5.0);
            }
        }

        // Extract price range
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        if (!productIds.isEmpty()) {
            List<BestPriceProjection> bestPrices = productPriceRepository.findBestPricesByProductIds(new ArrayList<>(productIds));
            if (!bestPrices.isEmpty()) {
                minPrice = bestPrices.stream()
                        .map(BestPriceProjection::getBestPrice)
                        .filter(Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null);

                maxPrice = bestPrices.stream()
                        .map(BestPriceProjection::getBestPrice)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
            }
        }

        long totalInteractions = events.size();

        return RecommendationProfile.builder()
                .preferredCategories(categoryWeights)
                .preferredBrands(brandWeights)
                .preferredSellers(sellerWeights)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .interactionFrequency(freqMap)
                .totalInteractions(totalInteractions)
                .build();
    }

    private void incrementWeight(Map<String, Double> map, String key, double value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        map.put(key, map.getOrDefault(key, 0.0) + value);
    }
}
