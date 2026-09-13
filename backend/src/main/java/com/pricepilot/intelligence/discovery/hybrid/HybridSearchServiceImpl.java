package com.pricepilot.intelligence.discovery.hybrid;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.*;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
import com.pricepilot.intelligence.discovery.specification.DiscoverySpecifications;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production implementation of HybridSearchService.
 * Coordinates structured lexical candidate retrieval, semantic vector retrieval,
 * candidate fusion, strict hard-filter enforcement, and deterministic final ranking.
 */
@Service
public class HybridSearchServiceImpl implements HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final String ENTITY_TYPE_PRODUCT = "PRODUCT";

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final QueryInterpreter queryInterpreter;
    private final DefaultSearchRelevanceScorer relevanceScorer;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final HybridCandidateFusionStrategy fusionStrategy;
    private final SemanticIntelligenceProperties properties;

    // Micrometer Observability Metrics
    private final Counter requestCounter;
    private final Counter degradedCounter;
    private final Counter failureCounter;
    private final Timer searchTimer;
    private final DistributionSummary structuredCandidateSummary;
    private final DistributionSummary semanticCandidateSummary;
    private final DistributionSummary fusedCandidateSummary;

    public HybridSearchServiceImpl(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            PriceAnalyticsService priceAnalyticsService,
            QueryInterpreter queryInterpreter,
            DefaultSearchRelevanceScorer relevanceScorer,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            HybridCandidateFusionStrategy fusionStrategy,
            SemanticIntelligenceProperties properties,
            MeterRegistry meterRegistry) {
        this.productRepository = Objects.requireNonNull(productRepository, "ProductRepository cannot be null");
        this.productPriceRepository = Objects.requireNonNull(productPriceRepository, "ProductPriceRepository cannot be null");
        this.priceAnalyticsService = Objects.requireNonNull(priceAnalyticsService, "PriceAnalyticsService cannot be null");
        this.queryInterpreter = Objects.requireNonNull(queryInterpreter, "QueryInterpreter cannot be null");
        this.relevanceScorer = Objects.requireNonNull(relevanceScorer, "DefaultSearchRelevanceScorer cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "EmbeddingService cannot be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null");
        this.fusionStrategy = Objects.requireNonNull(fusionStrategy, "HybridCandidateFusionStrategy cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");

        this.requestCounter = Counter.builder("pricepilot.hybrid.search.requests")
                .description("Total hybrid discovery search requests")
                .register(meterRegistry);
        this.degradedCounter = Counter.builder("pricepilot.hybrid.search.degraded")
                .description("Hybrid searches degraded to structured-only due to semantic subsystem failure")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.hybrid.search.failures")
                .description("Total hybrid search failures")
                .register(meterRegistry);
        this.searchTimer = Timer.builder("pricepilot.hybrid.search.latency")
                .description("Latency distribution of hybrid search operations")
                .register(meterRegistry);
        this.structuredCandidateSummary = DistributionSummary.builder("pricepilot.hybrid.search.structured.candidates")
                .description("Number of structured candidates retrieved")
                .register(meterRegistry);
        this.semanticCandidateSummary = DistributionSummary.builder("pricepilot.hybrid.search.semantic.candidates")
                .description("Number of semantic candidates retrieved")
                .register(meterRegistry);
        this.fusedCandidateSummary = DistributionSummary.builder("pricepilot.hybrid.search.fused.candidates")
                .description("Number of fused candidates evaluated")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverySearchResponseDTO search(DiscoverySearchRequestDTO request) {
        if (request == null) {
            return search(new HybridSearchRequest());
        }
        HybridSearchRequest hybridRequest = HybridSearchRequest.builder()
                .query(request.getQuery())
                .category(request.getCategory())
                .brand(request.getBrand())
                .minPrice(request.getMinPrice())
                .maxPrice(request.getMaxPrice())
                .minRating(request.getMinRating())
                .minDiscount(request.getMinDiscount())
                .inStock(request.getInStock())
                .dealQuality(request.getDealQuality())
                .sellerId(request.getSellerId())
                .sort(request.getSort())
                .page(request.getPage())
                .size(request.getSize())
                .build();
        return search(hybridRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverySearchResponseDTO search(HybridSearchRequest request) {
        Objects.requireNonNull(request, "HybridSearchRequest cannot be null");
        requestCounter.increment();
        long start = System.currentTimeMillis();

        try {
            // 1. Sanitize pagination parameters
            int page = Math.max(0, request.getPage());
            int size = Math.min(MAX_PAGE_SIZE, Math.max(1, request.getSize() > 0 ? request.getSize() : 10));

            // Validate sort parameter against allowed properties
            validateSort(request.getSort());

            // 2. Query Interpretation
            String rawQuery = request.getQuery() != null ? request.getQuery() : "";
            InterpretedQuery interpreted = queryInterpreter.interpret(rawQuery);

            // 3. Resolve effective filter parameters (explicit filters override interpreted intent)
            String effectiveCategory = (request.getCategory() != null && !request.getCategory().isBlank() && !"All".equalsIgnoreCase(request.getCategory()))
                    ? request.getCategory().trim()
                    : interpreted.getDetectedCategory();

            String effectiveBrand = (request.getBrand() != null && !request.getBrand().isBlank() && !"All".equalsIgnoreCase(request.getBrand()))
                    ? request.getBrand().trim()
                    : interpreted.getDetectedBrand();

            BigDecimal effectiveMinPrice = request.getMinPrice() != null ? request.getMinPrice() : interpreted.getMinPrice();
            BigDecimal effectiveMaxPrice = request.getMaxPrice() != null ? request.getMaxPrice() : interpreted.getMaxPrice();
            Boolean effectiveInStock = request.getInStock() != null ? request.getInStock() : interpreted.getInStockOnly();

            // 4. Structured Candidate Retrieval
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

            int structuredLimit = request.getStructuredCandidateLimit() > 0 ? request.getStructuredCandidateLimit() : 50;
            Page<ProductEntity> structuredPage = productRepository.findAll(spec, PageRequest.of(0, structuredLimit));
            List<ProductEntity> structuredCandidates = structuredPage.getContent();
            structuredCandidateSummary.record(structuredCandidates.size());

            Map<UUID, ProductEntity> productEntityMap = new HashMap<>();
            for (ProductEntity p : structuredCandidates) {
                if (p != null && p.getId() != null) {
                    productEntityMap.put(p.getId(), p);
                }
            }

            // 5. Semantic Candidate Retrieval (with Graceful Degradation)
            List<SimilaritySearchResult> semanticResults = Collections.emptyList();
            if (!rawQuery.trim().isEmpty() && properties.isEnabled()) {
                try {
                    int semanticLimit = request.getSemanticCandidateLimit() > 0 ? request.getSemanticCandidateLimit() : 50;
                    double minScore = request.getMinSimilarityScore() >= 0 ? request.getMinSimilarityScore() : properties.getMinSimilarityScore();

                    EmbeddingVector queryVector = embeddingService.generateEmbedding(rawQuery);

                    SimilaritySearchRequest simRequest = SimilaritySearchRequest.builder()
                            .queryVector(queryVector)
                            .entityType(ENTITY_TYPE_PRODUCT)
                            .modelName(embeddingService.getModelName())
                            .modelVersion(embeddingService.getModelVersion())
                            .topK(semanticLimit)
                            .minScore(minScore)
                            .build();

                    semanticResults = vectorStore.similaritySearch(simRequest);
                    semanticCandidateSummary.record(semanticResults.size());

                    // Batch fetch any semantic candidate products not already in memory
                    List<UUID> missingIds = new ArrayList<>();
                    for (SimilaritySearchResult r : semanticResults) {
                        try {
                            UUID id = UUID.fromString(r.getEntityId());
                            if (!productEntityMap.containsKey(id)) {
                                missingIds.add(id);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (!missingIds.isEmpty()) {
                        List<ProductEntity> missingEntities = productRepository.findAllByIdInWithPricesAndSellers(missingIds);
                        for (ProductEntity p : missingEntities) {
                            if (p != null && p.getId() != null) {
                                productEntityMap.put(p.getId(), p);
                            }
                        }
                    }
                } catch (Exception e) {
                    degradedCounter.increment();
                    log.warn("Semantic retrieval failed in hybrid search for query '{}', gracefully degrading to structured-only: {}",
                            rawQuery, e.getMessage());
                    semanticResults = Collections.emptyList();
                }
            }

            // 6. Hard Filter Enforcement (Pre-Fusion Validation on Semantic Candidates)
            List<SimilaritySearchResult> validatedSemanticResults = new ArrayList<>();
            for (SimilaritySearchResult r : semanticResults) {
                try {
                    UUID id = UUID.fromString(r.getEntityId());
                    ProductEntity p = productEntityMap.get(id);
                    if (p != null && passesTaxonomyAndArchivalFilters(p, effectiveCategory, effectiveBrand)) {
                        validatedSemanticResults.add(r);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            // 7. Candidate Set Fusion
            int maxFused = request.getMaxFusedCandidates() > 0 ? request.getMaxFusedCandidates() : 100;
            List<HybridCandidate> fusedCandidates = fusionStrategy.fuseCandidates(
                    structuredCandidates,
                    validatedSemanticResults,
                    productEntityMap,
                    request.getStructuredWeight(),
                    request.getSemanticWeight(),
                    maxFused
            );
            fusedCandidateSummary.record(fusedCandidates.size());

            if (fusedCandidates.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(page, size, interpreted, request.getSort(), duration);
            }

            // 8. Batch Price Loading & Hard Constraint Validation on Prices
            List<UUID> candidateIds = fusedCandidates.stream().map(HybridCandidate::getProductId).toList();
            List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(candidateIds);
            Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                    .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

            List<ScoredProductCandidate> scoredCandidates = new ArrayList<>();

            for (HybridCandidate hybridCandidate : fusedCandidates) {
                ProductEntity product = hybridCandidate.getProduct();
                List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(product.getId(), Collections.emptyList());

                BigDecimal bestPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getCurrentPrice)
                        .filter(Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal originalPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getOriginalPrice)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal maxDiscount = prodPrices.stream()
                        .map(ProductPriceEntity::getDiscountPercentage)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                // Check Price & Stock Hard Constraints (Crucial Principle: Hard filters remain authoritative!)
                if (!passesPriceAndStockFilters(bestPrice, prodPrices, effectiveMinPrice, effectiveMaxPrice, effectiveInStock, request.getSellerId(), request.getMinDiscount())) {
                    continue;
                }

                ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                        .product(product)
                        .currentBestPrice(bestPrice)
                        .originalPrice(originalPrice)
                        .discountPercentage(maxDiscount)
                        .build();

                // Compute deterministic discovery relevance score
                relevanceScorer.scoreCandidate(candidate, interpreted);

                // Enhance with Hybrid Provenance & Semantic Signal
                applyHybridProvenanceEnrichment(candidate, hybridCandidate);

                scoredCandidates.add(candidate);
            }

            if (scoredCandidates.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(page, size, interpreted, request.getSort(), duration);
            }

            // 9. Final Sorting
            String sortMode = request.getSort() != null ? request.getSort().trim().toLowerCase() : "relevance";
            applySorting(scoredCandidates, sortMode);

            // 10. Pagination Slicing
            long totalElements = scoredCandidates.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            int fromIndex = page * size;
            List<ScoredProductCandidate> pagedSlice;
            if (fromIndex >= scoredCandidates.size()) {
                pagedSlice = Collections.emptyList();
            } else {
                int toIndex = Math.min(fromIndex + size, scoredCandidates.size());
                pagedSlice = scoredCandidates.subList(fromIndex, toIndex);
            }

            // 11. Bounded Analytics Enrichment on Paged Slice
            List<DiscoveryProductDTO> discoveryProducts = enrichPagedSliceWithIntelligence(
                    pagedSlice, pricesByProductId, interpreted, page == 0
            );

            // 12. Extract Facets
            Set<String> distinctCategories = new TreeSet<>();
            Set<String> distinctBrands = new TreeSet<>();
            for (ScoredProductCandidate sc : scoredCandidates) {
                ProductEntity p = sc.getProduct();
                if (p.getCategory() != null) distinctCategories.add(p.getCategory());
                if (p.getBrand() != null) distinctBrands.add(p.getBrand());
            }

            long duration = System.currentTimeMillis() - start;
            searchTimer.record(duration, TimeUnit.MILLISECONDS);

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
            log.error("Hybrid search execution failed for query: {}", request.getQuery(), e);
            throw e;
        }
    }

    private boolean passesTaxonomyAndArchivalFilters(ProductEntity p, String effectiveCategory, String effectiveBrand) {
        if (p == null || p.isArchived()) {
            return false;
        }
        if (effectiveCategory != null && !effectiveCategory.isBlank()) {
            if (p.getCategory() == null || !p.getCategory().equalsIgnoreCase(effectiveCategory)) {
                return false;
            }
        }
        if (effectiveBrand != null && !effectiveBrand.isBlank()) {
            if (p.getBrand() == null || !p.getBrand().equalsIgnoreCase(effectiveBrand)) {
                return false;
            }
        }
        return true;
    }

    private boolean passesPriceAndStockFilters(
            BigDecimal bestPrice,
            List<ProductPriceEntity> prodPrices,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            UUID sellerId,
            BigDecimal minDiscount) {

        if (Boolean.TRUE.equals(inStock) && (prodPrices == null || prodPrices.isEmpty() || bestPrice == null)) {
            return false;
        }

        if (sellerId != null) {
            boolean matchesSeller = prodPrices.stream()
                    .anyMatch(pp -> pp.getSeller() != null && sellerId.equals(pp.getSeller().getId()));
            if (!matchesSeller) {
                return false;
            }
        }

        if (minPrice != null && bestPrice != null && bestPrice.compareTo(minPrice) < 0) {
            return false;
        }

        if (maxPrice != null && bestPrice != null && bestPrice.compareTo(maxPrice) > 0) {
            return false;
        }

        if (minDiscount != null && minDiscount.compareTo(BigDecimal.ZERO) > 0) {
            boolean hasDiscount = prodPrices.stream()
                    .anyMatch(pp -> pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(minDiscount) >= 0);
            if (!hasDiscount) {
                return false;
            }
        }

        return true;
    }

    private void applyHybridProvenanceEnrichment(ScoredProductCandidate candidate, HybridCandidate hybridCandidate) {
        List<String> reasons = new ArrayList<>(candidate.getRelevanceReasons());
        List<String> badges = new ArrayList<>(candidate.getBadges());
        double currentScore = candidate.getRelevanceScore();

        Double similarity = hybridCandidate.getSemanticSimilarityScore();

        if (hybridCandidate.getProvenance() == CandidateProvenance.BOTH) {
            badges.add("Semantic Match");
            if (similarity != null) {
                reasons.add(String.format("Matched by both keyword and semantic search (%d%% concept match)", Math.round(similarity * 100)));
            } else {
                reasons.add("Matched by both keyword search and semantic intelligence");
            }
            // Moderate boost for dual confirmation
            currentScore += 10.0;
        } else if (hybridCandidate.getProvenance() == CandidateProvenance.SEMANTIC) {
            badges.add("Semantic Discovery");
            if (similarity != null) {
                reasons.add(String.format("Discovered via semantic concept similarity (%d%% match)", Math.round(similarity * 100)));
                // Proportional semantic score contribution
                currentScore += (similarity * 20.0);
            } else {
                reasons.add("Discovered via semantic vector retrieval");
                currentScore += 10.0;
            }
        }

        candidate.setRelevanceScore(Math.round(currentScore * 10.0) / 10.0);
        candidate.setRelevanceReasons(reasons);
        candidate.setBadges(badges);
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

    private DiscoverySearchResponseDTO buildEmptyResponse(int page, int size, InterpretedQuery interpreted, String sort, long duration) {
        List<String> categories = Collections.emptyList();
        List<String> brands = Collections.emptyList();
        try {
            List<String> repoCats = productRepository.findDistinctCategories();
            if (repoCats != null) categories = repoCats;
            List<String> repoBrands = productRepository.findDistinctBrands();
            if (repoBrands != null) brands = repoBrands;
        } catch (Exception ignored) {
        }

        return DiscoverySearchResponseDTO.builder()
                .content(Collections.emptyList())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .interpretedQuery(interpreted)
                .appliedSort(sort)
                .executionTimeMs(duration)
                .availableCategories(categories)
                .availableBrands(brands)
                .build();
    }
}
