package com.pricepilot.recommendation;

import com.pricepilot.recommendation.dto.DashboardDTO;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.ai.RecommendationService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.savedproduct.SavedProductService;
import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.dto.UserInteractionEventResponseDTO;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.exception.ResourceNotFoundException;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final UserInteractionEventRepository eventRepository;
    private final ProductPriceRepository productPriceRepository;
    private final RecommendationService recommendationService;
    private final ProductService productService;
    private final UserInteractionEventService eventService;
    private final SavedProductService savedProductService;

    public DashboardServiceImpl(
            UserRepository userRepository,
            PriceWatchlistRepository watchlistRepository,
            UserInteractionEventRepository eventRepository,
            ProductPriceRepository productPriceRepository,
            RecommendationService recommendationService,
            ProductService productService,
            UserInteractionEventService eventService,
            SavedProductService savedProductService) {
        this.userRepository = userRepository;
        this.watchlistRepository = watchlistRepository;
        this.eventRepository = eventRepository;
        this.productPriceRepository = productPriceRepository;
        this.recommendationService = recommendationService;
        this.productService = productService;
        this.eventService = eventService;
        this.savedProductService = savedProductService;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "dashboard", key = "#userId")
    public DashboardDTO getDashboardData(UUID userId, String email) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Saved Products
        List<SavedProductResponseDTO> savedProducts = savedProductService.getSavedProducts(email);

        // 2. Watchlists and Price Drop Alerts
        List<PriceWatchlistEntity> watchlistEntities = watchlistRepository.findAllByUserIdWithProduct(userId);
        
        List<WatchlistResponseDTO> watchlists = watchlistEntities.stream()
                .map(WatchlistResponseDTO::fromEntity)
                .collect(Collectors.toList());

        List<WatchlistResponseDTO> priceDropAlerts = watchlists.stream()
                .filter(w -> w.isActive() && w.getCurrentBestPrice() != null && w.getTargetPrice() != null 
                        && w.getCurrentBestPrice().compareTo(w.getTargetPrice()) <= 0)
                .collect(Collectors.toList());

        long activePriceAlertsCount = priceDropAlerts.size();

        // 3. Activity and Recent Views / Clicks / Searches
        List<UserInteractionEventEntity> eventEntities = eventRepository.findByUserIdWithRelations(userId, PageRequest.of(0, 100));

        List<UserInteractionEventResponseDTO> recentActivity = eventEntities.stream()
                .limit(20)
                .map(eventService::mapToDTO)
                .collect(Collectors.toList());

        long totalActivitiesCount = eventRepository.countByUserId(userId);

        // Derive Recently Viewed Products from events
        List<ProductEntity> viewedProducts = eventEntities.stream()
                .filter(e -> e.getInteractionType() == InteractionType.PRODUCT_VIEW)
                .map(UserInteractionEventEntity::getProduct)
                .filter(Objects::nonNull)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        List<ProductResponseDTO> recentlyViewed = mapProductsToResponseDTOs(viewedProducts);

        // Derive Recent Searches
        List<String> recentSearches = eventEntities.stream()
                .filter(e -> e.getInteractionType() == InteractionType.SEARCH)
                .map(e -> e.getMetadata() != null ? (String) e.getMetadata().get("keyword") : null)
                .filter(Objects::nonNull)
                .filter(k -> !k.trim().isEmpty())
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        // Derive Most Clicked Sellers
        Map<String, Long> sellerClickCounts = eventEntities.stream()
                .filter(e -> e.getInteractionType() == InteractionType.SELLER_CLICK && e.getSeller() != null)
                .collect(Collectors.groupingBy(e -> e.getSeller().getName(), Collectors.counting()));

        List<Map<String, Object>> mostClickedSellers = sellerClickCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", entry.getKey());
                    map.put("count", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        // 4. Personalized Recommendations
        List<ProductResponseDTO> recommendations = recommendationService.getPersonalizedRecommendations(userId, 10);

        // 5. Trending Products
        List<ProductResponseDTO> trendingProducts = productService.getTrendingProducts(10);

        return DashboardDTO.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .savedCount(savedProducts.size())
                .watchlistCount(watchlists.size())
                .totalActivitiesCount(totalActivitiesCount)
                .activePriceAlertsCount(activePriceAlertsCount)
                .recommendations(recommendations)
                .recentlyViewed(recentlyViewed)
                .priceDropAlerts(priceDropAlerts)
                .trendingProducts(trendingProducts)
                .watchlists(watchlists)
                .savedProducts(savedProducts)
                .recentActivity(recentActivity)
                .recentSearches(recentSearches)
                .mostClickedSellers(mostClickedSellers)
                .build();
    }

    private List<ProductResponseDTO> mapProductsToResponseDTOs(List<ProductEntity> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> productIds = products.stream()
                .map(ProductEntity::getId)
                .collect(Collectors.toList());

        List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);

        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        return products.stream().map(product -> {
            ProductResponseDTO dto = ProductResponseDTO.fromEntity(product);
            List<ProductPriceEntity> productPrices = pricesByProductId.getOrDefault(product.getId(), List.of());
            dto.setPrices(productPrices.stream()
                    .map(priceEntity -> com.pricepilot.productprice.dto.ProductPriceResponseDTO.builder()
                            .id(priceEntity.getId())
                            .currentPrice(priceEntity.getCurrentPrice())
                            .originalPrice(priceEntity.getOriginalPrice())
                            .discountPercentage(priceEntity.getDiscountPercentage())
                            .productUrl(priceEntity.getProductUrl())
                            .lastUpdated(priceEntity.getLastUpdated())
                            .seller(com.pricepilot.seller.dto.SellerResponseDTO.fromEntity(priceEntity.getSeller()))
                            .createdAt(priceEntity.getCreatedAt())
                            .updatedAt(priceEntity.getUpdatedAt())
                            .build()
                    )
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }
}
