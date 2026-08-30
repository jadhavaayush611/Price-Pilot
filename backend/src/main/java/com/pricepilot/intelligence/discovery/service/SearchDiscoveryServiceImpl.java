package com.pricepilot.intelligence.discovery.service;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.*;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
import com.pricepilot.intelligence.discovery.specification.DiscoverySpecifications;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production implementation of SearchDiscoveryService.
 * Executes deterministic relevance scoring, bounded candidate retrieval, and bounded Phase 4 analytics enrichment.
 */
@Service
public class SearchDiscoveryServiceImpl implements SearchDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(SearchDiscoveryServiceImpl.class);
    private static final int MAX_CANDIDATE_POOL = 150;
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final QueryNormalizer queryNormalizer;
    private final QueryInterpreter queryInterpreter;
    private final DefaultSearchRelevanceScorer relevanceScorer;

    // Metrics
    private final Counter requestCounter;
    private final Counter emptyResultsCounter;
    private final Counter failureCounter;
    private final Timer searchLatencyTimer;

    public SearchDiscoveryServiceImpl(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            PriceAnalyticsService priceAnalyticsService,
            QueryNormalizer queryNormalizer,
            QueryInterpreter queryInterpreter,
            DefaultSearchRelevanceScorer relevanceScorer,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.priceAnalyticsService = priceAnalyticsService;
        this.queryNormalizer = queryNormalizer;
        this.queryInterpreter = queryInterpreter;
        this.relevanceScorer = relevanceScorer;

        this.requestCounter = Counter.builder("pricepilot.search.requests")
                .description("Total product discovery search requests")
                .register(meterRegistry);
        this.emptyResultsCounter = Counter.builder("pricepilot.search.empty_results")
                .description("Search queries returning zero results")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.search.failures")
                .description("Search discovery execution failures")
                .register(meterRegistry);
        this.searchLatencyTimer = Timer.builder("pricepilot.search.latency")
                .description("Search discovery latency")
                .register(meterRegistry);
    }

    @Override
    @Cacheable(value = "product-searches", key = "T(java.util.Objects).hash(#request.query, #request.category, #request.brand, #request.minPrice, #request.maxPrice, #request.inStock, #request.dealQuality, #request.sort, #request.page, #request.size)")
    public DiscoverySearchResponseDTO searchAndDiscover(DiscoverySearchRequestDTO request) {
        requestCounter.increment();
        long start = System.currentTimeMillis();

        try {
            // 1. Sanitize pagination parameters
            int page = Math.max(0, request.getPage());
            int size = Math.min(MAX_PAGE_SIZE, Math.max(1, request.getSize() > 0 ? request.getSize() : 10));

            // Validate sort parameter against allowed properties
            validateSort(request.getSort());

            // 2. Interpret search query
            String rawQuery = request.getQuery() != null ? request.getQuery() : "";
            InterpretedQuery interpreted = queryInterpreter.interpret(rawQuery);

            // 3. Resolve effective filter parameters (explicit parameters take priority over interpreted intent)
            String effectiveCategory = (request.getCategory() != null && !request.getCategory().isBlank() && !"All".equalsIgnoreCase(request.getCategory()))
                    ? request.getCategory().trim()
                    : interpreted.getDetectedCategory();

            String effectiveBrand = (request.getBrand() != null && !request.getBrand().isBlank() && !"All".equalsIgnoreCase(request.getBrand()))
                    ? request.getBrand().trim()
                    : interpreted.getDetectedBrand();

            BigDecimal effectiveMinPrice = request.getMinPrice() != null ? request.getMinPrice() : interpreted.getMinPrice();
            BigDecimal effectiveMaxPrice = request.getMaxPrice() != null ? request.getMaxPrice() : interpreted.getMaxPrice();
            Boolean effectiveInStock = request.getInStock() != null ? request.getInStock() : interpreted.getInStockOnly();

            // 4. Build database-side query Specification
            List<String> tokens = interpreted.getSearchTokens();
            Specification<ProductEntity> spec = DiscoverySpecifications.buildDiscoverySpec(
                    tokens,
                    effectiveCategory,
                    effectiveBrand,
                    effectiveMinPrice,
                    effectiveMaxPrice,
                    effectiveInStock,
                    request.getSellerId()
            );

            // 5. Bounded Candidate Retrieval: retrieve up to MAX_CANDIDATE_POOL to avoid memory explosion
            int candidatePoolLimit = Math.min(MAX_CANDIDATE_POOL, Math.max(50, (page + 1) * size * 2));
            Pageable candidatePageRequest = PageRequest.of(0, candidatePoolLimit);
            Page<ProductEntity> candidatePage = productRepository.findAll(spec, candidatePageRequest);
            long totalElements = candidatePage.getTotalElements();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            if (candidatePage.isEmpty()) {
                emptyResultsCounter.increment();
                long duration = System.currentTimeMillis() - start;
                return DiscoverySearchResponseDTO.builder()
                        .content(Collections.emptyList())
                        .page(page)
                        .size(size)
                        .totalElements(0)
                        .totalPages(0)
                        .interpretedQuery(interpreted)
                        .appliedSort(request.getSort())
                        .executionTimeMs(duration)
                        .availableCategories(productRepository.findDistinctCategories())
                        .availableBrands(productRepository.findDistinctBrands())
                        .build();
            }

            List<ProductEntity> candidateProducts = candidatePage.getContent();
            List<UUID> candidateIds = candidateProducts.stream().map(ProductEntity::getId).toList();

            // 6. Batch load prices for candidates to eliminate N+1 queries
            List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(candidateIds);
            Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                    .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

            // 7. Relevance scoring of candidates
            List<ScoredProductCandidate> scoredCandidates = new ArrayList<>();
            for (ProductEntity product : candidateProducts) {
                List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(product.getId(), Collections.emptyList());
                BigDecimal bestPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getCurrentPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal originalPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getOriginalPrice)
                        .max(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal maxDiscount = prodPrices.stream()
                        .map(ProductPriceEntity::getDiscountPercentage)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                        .product(product)
                        .currentBestPrice(bestPrice)
                        .originalPrice(originalPrice)
                        .discountPercentage(maxDiscount)
                        .build();

                // Score candidate against interpreted query
                relevanceScorer.scoreCandidate(candidate, interpreted);
                scoredCandidates.add(candidate);
            }

            // 8. Sorting (Explicit user sort overrides default relevance ranking)
            String sortMode = request.getSort() != null ? request.getSort().trim().toLowerCase() : "relevance";
            applySorting(scoredCandidates, sortMode);

            // 9. Pagination slicing
            int fromIndex = page * size;
            List<ScoredProductCandidate> pagedSlice;
            if (fromIndex >= scoredCandidates.size()) {
                pagedSlice = Collections.emptyList();
            } else {
                int toIndex = Math.min(fromIndex + size, scoredCandidates.size());
                pagedSlice = scoredCandidates.subList(fromIndex, toIndex);
            }

            // 10. Bounded Phase 4 Analytics Enrichment (ONLY on the paged slice)
            List<DiscoveryProductDTO> discoveryProducts = enrichPagedSliceWithIntelligence(
                    pagedSlice, pricesByProductId, interpreted, page == 0);

            // 11. Extract Available Facets
            Set<String> distinctCategories = new TreeSet<>();
            Set<String> distinctBrands = new TreeSet<>();
            for (ProductEntity p : candidateProducts) {
                if (p.getCategory() != null) distinctCategories.add(p.getCategory());
                if (p.getBrand() != null) distinctBrands.add(p.getBrand());
            }

            long duration = System.currentTimeMillis() - start;
            searchLatencyTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

            return DiscoverySearchResponseDTO.builder()
                    .content(discoveryProducts)
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .interpretedQuery(interpreted)
                    .appliedSort(sortMode)
                    .executionTimeMs(duration)
                    .availableCategories(new ArrayList<>(distinctCategories))
                    .availableBrands(new ArrayList<>(distinctBrands))
                    .build();

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to execute product discovery search for query: {}", request.getQuery(), e);
            throw e;
        }
    }

    private void validateSort(String sortStr) {
        if (sortStr == null || sortStr.trim().isEmpty() || sortStr.trim().equalsIgnoreCase("default") || sortStr.trim().equalsIgnoreCase("relevance")) {
            return;
        }
        String cleanSort = sortStr.trim().toLowerCase();
        if (Set.of("price-asc", "price,asc", "price_low", "price", "price-desc", "price,desc", "price_high", "discount-desc", "discount,desc", "discount", "newest").contains(cleanSort)) {
            return;
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        if ("created_at".equalsIgnoreCase(property)) property = "createdAt";
        else if ("updated_at".equalsIgnoreCase(property)) property = "updatedAt";
        else if ("image_url".equalsIgnoreCase(property)) property = "imageUrl";

        Set<String> validProperties = Set.of(
                "id", "name", "brand", "category", "description", "imageUrl", "archived", "createdAt", "updatedAt"
        );
        if (!validProperties.contains(property)) {
            throw new IllegalArgumentException("Invalid sort property: " + property);
        }
    }

    private void applySorting(List<ScoredProductCandidate> candidates, String sortMode) {
        switch (sortMode) {
            case "price-asc", "price,asc", "price_low", "price" -> candidates.sort((c1, c2) -> {
                BigDecimal p1 = c1.getCurrentBestPrice() != null ? c1.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
                BigDecimal p2 = c2.getCurrentBestPrice() != null ? c2.getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
                int comp = p1.compareTo(p2);
                if (comp != 0) return comp;
                return c1.getProductId().compareTo(c2.getProductId());
            });
            case "price-desc", "price,desc", "price_high" -> candidates.sort((c1, c2) -> {
                BigDecimal p1 = c1.getCurrentBestPrice() != null ? c1.getCurrentBestPrice() : BigDecimal.ZERO;
                BigDecimal p2 = c2.getCurrentBestPrice() != null ? c2.getCurrentBestPrice() : BigDecimal.ZERO;
                int comp = p2.compareTo(p1);
                if (comp != 0) return comp;
                return c1.getProductId().compareTo(c2.getProductId());
            });
            case "discount-desc", "discount,desc", "discount" -> candidates.sort((c1, c2) -> {
                BigDecimal d1 = c1.getDiscountPercentage() != null ? c1.getDiscountPercentage() : BigDecimal.ZERO;
                BigDecimal d2 = c2.getDiscountPercentage() != null ? c2.getDiscountPercentage() : BigDecimal.ZERO;
                int comp = d2.compareTo(d1);
                if (comp != 0) return comp;
                return c1.getProductId().compareTo(c2.getProductId());
            });
            case "newest", "createdat,desc", "created_at,desc" -> candidates.sort((c1, c2) -> {
                var d1 = c1.getProduct().getCreatedAt();
                var d2 = c2.getProduct().getCreatedAt();
                int comp = (d1 != null && d2 != null) ? d2.compareTo(d1) : 0;
                if (comp != 0) return comp;
                return c1.getProductId().compareTo(c2.getProductId());
            });
            default ->
                // Default: Deterministic relevance scoring
                candidates.sort(relevanceScorer.getDeterministicComparator());
        }
    }

    private List<DiscoveryProductDTO> enrichPagedSliceWithIntelligence(
            List<ScoredProductCandidate> slice,
            Map<UUID, List<ProductPriceEntity>> pricesByProductId,
            InterpretedQuery interpreted,
            boolean isFirstPage) {

        List<DiscoveryProductDTO> results = new ArrayList<>();
        boolean bestMatchAssigned = false;

        for (int i = 0; i < slice.size(); i++) {
            ScoredProductCandidate candidate = slice.get(i);
            ProductEntity entity = candidate.getProduct();
            List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(entity.getId(), Collections.emptyList());

            List<ProductPriceSearchResultDTO> priceDTOs = prodPrices.stream()
                    .map(ProductPriceSearchResultDTO::fromEntity)
                    .collect(Collectors.toList());

            // Phase 4 Intelligence enrichment (defensive isolation)
            com.pricepilot.intelligence.analytics.model.DealQuality dealQuality = null;
            com.pricepilot.intelligence.analytics.model.PriceTrend trend = null;
            com.pricepilot.intelligence.analytics.model.PurchaseSignal signal = null;
            Boolean isHistLow = null;

            try {
                ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(entity.getId());
                if (analytics != null) {
                    dealQuality = analytics.getDealQuality();
                    trend = analytics.getTrend();
                    signal = analytics.getPurchaseSignal();

                    if (analytics.getHistoricalMin() != null && candidate.getCurrentBestPrice() != null
                            && candidate.getCurrentBestPrice().compareTo(analytics.getHistoricalMin()) <= 0) {
                        isHistLow = true;
                    }

                    candidate.setDealQuality(dealQuality);
                    candidate.setPurchaseSignal(signal);
                    candidate.setIsHistoricalLow(isHistLow);
                    relevanceScorer.scoreCandidate(candidate, interpreted);
                }
            } catch (Exception e) {
                log.warn("Price analytics unavailable for discovery candidate {}: {}", entity.getId(), e.getMessage());
            }

            List<String> badges = new ArrayList<>(candidate.getBadges());
            List<String> reasons = new ArrayList<>(candidate.getRelevanceReasons());

            // Top result badge on page 0
            if (isFirstPage && i == 0 && candidate.getRelevanceScore() >= 40.0 && !bestMatchAssigned) {
                badges.add(0, "Best Match");
                bestMatchAssigned = true;
            }

            DiscoveryProductDTO dto = DiscoveryProductDTO.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .brand(entity.getBrand())
                    .category(entity.getCategory())
                    .description(entity.getDescription())
                    .imageUrl(entity.getImageUrl())
                    .archived(entity.isArchived())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .currentBestPrice(candidate.getCurrentBestPrice())
                    .originalPrice(candidate.getOriginalPrice())
                    .discountPercentage(candidate.getDiscountPercentage())
                    .prices(priceDTOs)
                    .inStock(!prodPrices.isEmpty())
                    .relevanceScore(candidate.getRelevanceScore())
                    .discoveryBadges(badges)
                    .discoveryReasons(reasons)
                    .dealQuality(dealQuality)
                    .priceTrend(trend)
                    .purchaseSignal(signal)
                    .isHistoricalLow(isHistLow)
                    .build();

            results.add(dto);
        }

        return results;
    }

    @Override
    public List<SearchSuggestionDTO> getSuggestions(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        int maxSuggestions = Math.min(10, Math.max(1, limit > 0 ? limit : 6));
        String normalized = queryNormalizer.normalize(query);
        List<SearchSuggestionDTO> suggestions = new ArrayList<>();

        // 1. Match Brands
        List<String> brands = productRepository.findDistinctBrands();
        for (String brand : brands) {
            if (brand != null && queryNormalizer.normalize(brand).startsWith(normalized)) {
                suggestions.add(SearchSuggestionDTO.builder()
                        .text(brand)
                        .type("BRAND")
                        .brand(brand)
                        .build());
                if (suggestions.size() >= maxSuggestions) return suggestions;
            }
        }

        // 2. Match Categories
        List<String> categories = productRepository.findDistinctCategories();
        for (String category : categories) {
            if (category != null && queryNormalizer.normalize(category).startsWith(normalized)) {
                suggestions.add(SearchSuggestionDTO.builder()
                        .text(category)
                        .type("CATEGORY")
                        .category(category)
                        .build());
                if (suggestions.size() >= maxSuggestions) return suggestions;
            }
        }

        // 3. Match Products starting with prefix
        List<ProductEntity> products = productRepository.findByNameStartingWithIgnoreCase(
                normalized, PageRequest.of(0, maxSuggestions - suggestions.size()));

        for (ProductEntity p : products) {
            suggestions.add(SearchSuggestionDTO.builder()
                    .text(p.getName())
                    .type("PRODUCT")
                    .productId(p.getId())
                    .category(p.getCategory())
                    .brand(p.getBrand())
                    .build());
            if (suggestions.size() >= maxSuggestions) break;
        }

        return suggestions;
    }
}
