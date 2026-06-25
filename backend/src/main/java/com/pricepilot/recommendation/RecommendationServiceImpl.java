package com.pricepilot.recommendation;

import com.pricepilot.ai.RecommendationService;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.analytics.ProductAnalyticsEntity;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SavedProductRepository savedProductRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final RecommendationProfileService profileService;

    public RecommendationServiceImpl(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            SavedProductRepository savedProductRepository,
            PriceWatchlistRepository watchlistRepository,
            RecommendationProfileService profileService) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.savedProductRepository = savedProductRepository;
        this.watchlistRepository = watchlistRepository;
        this.profileService = profileService;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "recommendations", key = "T(java.util.Objects).hash(#userId, #limit)")
    public List<ProductResponseDTO> getPersonalizedRecommendations(UUID userId, int limit) {
        // 1. Get exclusions (already saved or watchlisted)
        Set<UUID> excludedIds = new HashSet<>();
        List<SavedProductEntity> saved = savedProductRepository.findAllByUserIdWithProduct(userId);
        for (SavedProductEntity sp : saved) {
            if (sp.getProduct() != null) {
                excludedIds.add(sp.getProduct().getId());
            }
        }
        List<PriceWatchlistEntity> watchlists = watchlistRepository.findAllByUserIdWithProduct(userId);
        for (PriceWatchlistEntity pw : watchlists) {
            if (pw.getProduct() != null) {
                excludedIds.add(pw.getProduct().getId());
            }
        }

        // Add dummy UUID to prevent SQL issues if empty
        if (excludedIds.isEmpty()) {
            excludedIds.add(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        // 2. Get User Preference Profile
        RecommendationProfile profile = profileService.getProfile(userId);

        // 3. Select candidates
        List<ProductEntity> candidates;
        
        List<String> topCategories = profile.getPreferredCategories().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> topBrands = profile.getPreferredBrands().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topCategories.isEmpty() && topBrands.isEmpty()) {
            // Cold start: Fetch general popular products that are active
            candidates = productRepository.findActiveProductsExcluding(excludedIds, PageRequest.of(0, 200));
        } else {
            // Hot start: Fetch products matching category/brand + fallback popular ones if candidates are low
            if (topCategories.isEmpty()) topCategories.add("NONE_PLACEHOLDER");
            if (topBrands.isEmpty()) topBrands.add("NONE_PLACEHOLDER");
            
            candidates = productRepository.findCandidateProducts(excludedIds, topCategories, topBrands, PageRequest.of(0, 200));
            if (candidates.size() < limit) {
                List<ProductEntity> fallbacks = productRepository.findActiveProductsExcluding(excludedIds, PageRequest.of(0, 100));
                Set<UUID> loadedIds = candidates.stream().map(ProductEntity::getId).collect(Collectors.toSet());
                for (ProductEntity f : fallbacks) {
                    if (!loadedIds.contains(f.getId())) {
                        candidates.add(f);
                    }
                }
            }
        }

        // 4. Score candidates
        List<ScoredProduct> scoredProducts = new ArrayList<>();
        for (ProductEntity p : candidates) {
            double score = calculateRecommendationScore(p, profile, saved, watchlists);
            scoredProducts.add(new ScoredProduct(p, score));
        }

        // 5. Rank and return top limit
        List<ProductEntity> recommendedProducts = scoredProducts.stream()
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .map(ScoredProduct::getProduct)
                .collect(Collectors.toList());

        // 6. Map to DTOs in a single batch (no N+1 queries)
        return mapProductsToResponseDTOs(recommendedProducts);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "recommendations", key = "T(java.util.Objects).hash(#userId, #category, #brand, #minPrice, #maxPrice, #sort, #page, #size)")
    public PageResponse<ProductResponseDTO> getPersonalizedRecommendations(
            UUID userId,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sort,
            int page,
            int size) {
        
        // 1. Get a larger pool of scored recommendations (e.g. 200 items)
        List<ProductResponseDTO> allScored = getPersonalizedRecommendations(userId, 200);

        // 2. Apply filtering
        List<ProductResponseDTO> filtered = allScored.stream()
                .filter(p -> {
                    if (category != null && !category.trim().isEmpty() && !p.getCategory().equalsIgnoreCase(category.trim())) {
                        return false;
                    }
                    if (brand != null && !brand.trim().isEmpty() && (p.getBrand() == null || !p.getBrand().equalsIgnoreCase(brand.trim()))) {
                        return false;
                    }
                    BigDecimal bestPrice = getBestPrice(p);
                    if (bestPrice != null) {
                        if (minPrice != null && bestPrice.compareTo(minPrice) < 0) {
                            return false;
                        }
                        if (maxPrice != null && bestPrice.compareTo(maxPrice) > 0) {
                            return false;
                        }
                    } else if (minPrice != null || maxPrice != null) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 3. Apply sorting
        if (sort != null && !sort.trim().isEmpty()) {
            String sortClean = sort.trim().toLowerCase();
            if (sortClean.equals("price-asc") || sortClean.equals("price,asc")) {
                filtered.sort((p1, p2) -> {
                    BigDecimal pr1 = getBestPrice(p1);
                    BigDecimal pr2 = getBestPrice(p2);
                    if (pr1 == null) return 1;
                    if (pr2 == null) return -1;
                    return pr1.compareTo(pr2);
                });
            } else if (sortClean.equals("price-desc") || sortClean.equals("price,desc")) {
                filtered.sort((p1, p2) -> {
                    BigDecimal pr1 = getBestPrice(p1);
                    BigDecimal pr2 = getBestPrice(p2);
                    if (pr1 == null) return 1;
                    if (pr2 == null) return -1;
                    return pr2.compareTo(pr1);
                });
            } else if (sortClean.equals("name-asc") || sortClean.equals("name,asc") || sortClean.equals("name")) {
                filtered.sort(Comparator.comparing(ProductResponseDTO::getName, String.CASE_INSENSITIVE_ORDER));
            } else if (sortClean.equals("name-desc") || sortClean.equals("name,desc")) {
                filtered.sort((p1, p2) -> p2.getName().compareToIgnoreCase(p1.getName()));
            }
        }

        // 4. Paginate in memory
        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        int end = Math.min(start + size, totalElements);

        List<ProductResponseDTO> pageContent = new ArrayList<>();
        if (start < totalElements) {
            pageContent = filtered.subList(start, end);
        }

        return PageResponse.<ProductResponseDTO>builder()
                .content(pageContent)
                .number(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(pageContent.isEmpty())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "recommendations", key = "'similar_' + T(java.util.Objects).hash(#productId, #limit)")
    public List<ProductResponseDTO> getSimilarProducts(UUID productId, int limit) {
        ProductEntity target = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // Get candidates that share category or brand
        List<ProductEntity> candidates = productRepository.findSimilarCandidates(
                productId, target.getCategory(), target.getBrand(), PageRequest.of(0, 100));

        // If candidates are less than limit, fetch some active fallbacks
        if (candidates.size() < limit) {
            Set<UUID> excluded = new HashSet<>();
            excluded.add(productId);
            candidates.stream().forEach(c -> excluded.add(c.getId()));
            
            List<ProductEntity> fallbacks = productRepository.findActiveProductsExcluding(excluded, PageRequest.of(0, 50));
            candidates.addAll(fallbacks);
        }

        // Calculate similarity score
        List<ScoredProduct> scored = new ArrayList<>();
        BigDecimal targetBestPrice = getBestPrice(target);

        for (ProductEntity c : candidates) {
            double score = calculateSimilarityScore(c, target, targetBestPrice);
            scored.add(new ScoredProduct(c, score));
        }

        List<ProductEntity> similarProducts = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .map(ScoredProduct::getProduct)
                .collect(Collectors.toList());

        return mapProductsToResponseDTOs(similarProducts);
    }

    private double calculateRecommendationScore(
            ProductEntity product,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists) {

        double score = 0.0;

        // A. Category Match
        Double catWeight = profile.getPreferredCategories().get(product.getCategory());
        if (catWeight != null) {
            score += catWeight * 10.0;
        }

        // B. Brand Match
        if (product.getBrand() != null) {
            Double brandWeight = profile.getPreferredBrands().get(product.getBrand());
            if (brandWeight != null) {
                score += brandWeight * 8.0;
            }
        }

        // C. Seller Match
        BigDecimal bestPrice = getBestPrice(product);
        if (product.getProductPrices() != null) {
            for (ProductPriceEntity pp : product.getProductPrices()) {
                if (pp.getSeller() != null) {
                    Double sellerWeight = profile.getPreferredSellers().get(pp.getSeller().getName());
                    if (sellerWeight != null) {
                        score += sellerWeight * 5.0;
                    }
                }
            }
        }

        // D. Price Range Match
        if (profile.getMinPrice() != null && profile.getMaxPrice() != null && bestPrice != null) {
            if (bestPrice.compareTo(profile.getMinPrice()) >= 0 && bestPrice.compareTo(profile.getMaxPrice()) <= 0) {
                score += 15.0; // Bonus for being within preferred range
            } else {
                // If slightly outside (within 20% margin)
                BigDecimal margin = profile.getMaxPrice().subtract(profile.getMinPrice()).multiply(BigDecimal.valueOf(0.2));
                BigDecimal lowerBound = profile.getMinPrice().subtract(margin);
                BigDecimal upperBound = profile.getMaxPrice().add(margin);
                if (bestPrice.compareTo(lowerBound) >= 0 && bestPrice.compareTo(upperBound) <= 0) {
                    score += 5.0;
                }
            }
        }

        // E. Anchor Similarity (bonus if candidate category matches saved/watchlisted category)
        for (SavedProductEntity sp : saved) {
            if (sp.getProduct() != null && sp.getProduct().getCategory().equals(product.getCategory())) {
                score += 5.0;
            }
        }
        for (PriceWatchlistEntity pw : watchlists) {
            if (pw.getProduct() != null && pw.getProduct().getCategory().equals(product.getCategory())) {
                score += 10.0;
            }
        }

        // F. Trending Score (Analytics)
        ProductAnalyticsEntity analytics = product.getAnalytics();
        if (analytics != null) {
            double trendingScore = analytics.getViewCount() * 1.0 +
                    analytics.getSaveCount() * 5.0 +
                    analytics.getWatchlistCount() * 10.0 +
                    analytics.getPriceChangeCount() * 2.0;
            score += trendingScore * 0.1;

            // Popularity
            score += analytics.getViewCount() * 0.05;
        }

        // G. Price Drop Activity
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (product.getProductPrices() != null) {
            for (ProductPriceEntity pp : product.getProductPrices()) {
                if (pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(maxDiscount) > 0) {
                    maxDiscount = pp.getDiscountPercentage();
                }
            }
        }
        score += maxDiscount.doubleValue() * 0.5;

        return score;
    }

    private double calculateSimilarityScore(ProductEntity candidate, ProductEntity target, BigDecimal targetBestPrice) {
        double score = 0.0;

        // Category Match
        if (candidate.getCategory().equals(target.getCategory())) {
            score += 50.0;
        }

        // Brand Match
        if (target.getBrand() != null && target.getBrand().equals(candidate.getBrand())) {
            score += 30.0;
        }

        // Price Similarity
        BigDecimal candBestPrice = getBestPrice(candidate);
        if (candBestPrice != null && targetBestPrice != null && targetBestPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = candBestPrice.subtract(targetBestPrice).abs();
            BigDecimal ratio = diff.divide(targetBestPrice, 4, RoundingMode.HALF_UP);
            double pctDiff = ratio.doubleValue();

            if (pctDiff <= 0.1) {
                score += 20.0;
            } else if (pctDiff <= 0.2) {
                score += 10.0;
            } else if (pctDiff <= 0.3) {
                score += 5.0;
            }
        }

        // Trending Score (Analytics)
        ProductAnalyticsEntity analytics = candidate.getAnalytics();
        if (analytics != null) {
            double trendingScore = analytics.getViewCount() * 1.0 +
                    analytics.getSaveCount() * 5.0 +
                    analytics.getWatchlistCount() * 10.0 +
                    analytics.getPriceChangeCount() * 2.0;
            score += trendingScore * 0.05;
        }

        // Discount
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (candidate.getProductPrices() != null) {
            for (ProductPriceEntity pp : candidate.getProductPrices()) {
                if (pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(maxDiscount) > 0) {
                    maxDiscount = pp.getDiscountPercentage();
                }
            }
        }
        score += maxDiscount.doubleValue() * 0.3;

        return score;
    }

    private BigDecimal getBestPrice(ProductEntity product) {
        if (product.getProductPrices() == null || product.getProductPrices().isEmpty()) {
            return null;
        }
        return product.getProductPrices().stream()
                .map(ProductPriceEntity::getCurrentPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal getBestPrice(ProductResponseDTO product) {
        if (product.getPrices() == null || product.getPrices().isEmpty()) {
            return null;
        }
        return product.getPrices().stream()
                .map(com.pricepilot.productprice.dto.ProductPriceResponseDTO::getCurrentPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
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

    private static class ScoredProduct {
        private final ProductEntity product;
        private final double score;

        public ScoredProduct(ProductEntity product, double score) {
            this.product = product;
            this.score = score;
        }

        public ProductEntity getProduct() {
            return product;
        }

        public double getScore() {
            return score;
        }
    }
}
