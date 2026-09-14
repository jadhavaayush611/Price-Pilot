package com.pricepilot.intelligence.alternative.service;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.scoring.AlternativeScoringStrategy;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.intent.*;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.specification.DiscoverySpecifications;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductText;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production implementation of AlternativeFinderService.
 * Coordinates product-driven and query-driven discovery, semantic vector candidate retrieval,
 * structured filter validation, multi-dimensional alternative scoring, and analytics enrichment.
 *
 * Core Principle:
 * "Semantic retrieval is for candidate discovery. Deterministic PricePilot intelligence
 * remains the source of truth for final ranking, filtering, and product facts."
 */
@Service
public class AlternativeFinderServiceImpl implements AlternativeFinderService {

    private static final Logger log = LoggerFactory.getLogger(AlternativeFinderServiceImpl.class);
    private static final int MAX_RESULTS_LIMIT = 20;
    private static final int MAX_CANDIDATES_LIMIT = 50;
    private static final String ENTITY_TYPE_PRODUCT = "PRODUCT";

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final AlternativeScoringStrategy scoringStrategy;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final CanonicalProductTextBuilder canonicalProductTextBuilder;
    private final HybridSearchService hybridSearchService;
    private final ShoppingQueryInterpreter shoppingQueryInterpreter;
    private final ShoppingQueryIntentValidator intentValidator;
    private final QueryNormalizer queryNormalizer;
    private final SemanticIntelligenceProperties properties;

    // Observability Metrics
    private final Counter requestCounter;
    private final Counter failureCounter;
    private final Counter degradedCounter;
    private final Timer searchTimer;
    private final DistributionSummary candidatesSummary;
    private final DistributionSummary resultsSummary;

    public AlternativeFinderServiceImpl(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            PriceAnalyticsService priceAnalyticsService,
            AlternativeScoringStrategy scoringStrategy,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            CanonicalProductTextBuilder canonicalProductTextBuilder,
            HybridSearchService hybridSearchService,
            ShoppingQueryInterpreter shoppingQueryInterpreter,
            ShoppingQueryIntentValidator intentValidator,
            QueryNormalizer queryNormalizer,
            SemanticIntelligenceProperties properties,
            MeterRegistry meterRegistry) {
        this.productRepository = Objects.requireNonNull(productRepository, "ProductRepository cannot be null");
        this.productPriceRepository = Objects.requireNonNull(productPriceRepository, "ProductPriceRepository cannot be null");
        this.priceAnalyticsService = Objects.requireNonNull(priceAnalyticsService, "PriceAnalyticsService cannot be null");
        this.scoringStrategy = Objects.requireNonNull(scoringStrategy, "AlternativeScoringStrategy cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "EmbeddingService cannot be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null");
        this.canonicalProductTextBuilder = Objects.requireNonNull(canonicalProductTextBuilder, "CanonicalProductTextBuilder cannot be null");
        this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "HybridSearchService cannot be null");
        this.shoppingQueryInterpreter = Objects.requireNonNull(shoppingQueryInterpreter, "ShoppingQueryInterpreter cannot be null");
        this.intentValidator = Objects.requireNonNull(intentValidator, "ShoppingQueryIntentValidator cannot be null");
        this.queryNormalizer = Objects.requireNonNull(queryNormalizer, "QueryNormalizer cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");

        this.requestCounter = Counter.builder("pricepilot.alternative.requests")
                .description("Total alternative discovery requests")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.alternative.failures")
                .description("Total alternative discovery failures")
                .register(meterRegistry);
        this.degradedCounter = Counter.builder("pricepilot.alternative.degraded")
                .description("Alternative searches degraded to structured-only due to semantic subsystem failure")
                .register(meterRegistry);
        this.searchTimer = Timer.builder("pricepilot.alternative.latency")
                .description("Latency of alternative discovery operations")
                .register(meterRegistry);
        this.candidatesSummary = DistributionSummary.builder("pricepilot.alternative.candidates")
                .description("Number of alternative candidates retrieved")
                .register(meterRegistry);
        this.resultsSummary = DistributionSummary.builder("pricepilot.alternative.results")
                .description("Number of alternatives returned to client")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findAlternativesForProduct(UUID productId, AlternativeRequest request) {
        Objects.requireNonNull(productId, "Product ID cannot be null");
        requestCounter.increment();
        long start = System.currentTimeMillis();

        AlternativeRequest effectiveRequest = request != null ? request : AlternativeRequest.builder().productId(productId).build();
        AlternativeType effectiveType = effectiveRequest.getType() != null ? effectiveRequest.getType() : AlternativeType.SIMILAR;
        int limit = Math.min(MAX_RESULTS_LIMIT, effectiveRequest.getEffectiveLimit());

        try {
            // 1. Fetch Source Product
            ProductEntity sourceProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            // Load source product prices
            List<ProductPriceEntity> sourcePrices = productPriceRepository.findPricesWithSellersByProductIds(List.of(productId));
            BigDecimal srcBestPrice = sourcePrices.stream()
                    .map(ProductPriceEntity::getCurrentPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null);

            BigDecimal srcOrigPrice = sourcePrices.stream()
                    .map(ProductPriceEntity::getOriginalPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(null);

            BigDecimal srcDiscount = sourcePrices.stream()
                    .map(ProductPriceEntity::getDiscountPercentage)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            DealQuality srcDealQuality = null;
            try {
                ProductAnalyticsResponseDTO srcAnalytics = priceAnalyticsService.getProductAnalytics(productId);
                if (srcAnalytics != null) {
                    srcDealQuality = srcAnalytics.getDealQuality();
                }
            } catch (Exception ignored) {
            }

            Double srcRating = calculateDeterministicRating(productId);

            SourceProductContextDTO sourceContext = SourceProductContextDTO.builder()
                    .id(sourceProduct.getId())
                    .name(sourceProduct.getName())
                    .brand(sourceProduct.getBrand())
                    .category(sourceProduct.getCategory())
                    .description(sourceProduct.getDescription())
                    .imageUrl(sourceProduct.getImageUrl())
                    .currentBestPrice(srcBestPrice)
                    .originalPrice(srcOrigPrice)
                    .discountPercentage(srcDiscount)
                    .rating(srcRating)
                    .dealQuality(srcDealQuality)
                    .build();

            // 2. Candidate Retrieval: Vector Similarity + Structured Taxonomy
            Map<UUID, ProductEntity> candidateEntityMap = new HashMap<>();
            Map<UUID, Double> similarityScoresMap = new HashMap<>();

            // A. Semantic Vector Retrieval
            if (properties.isEnabled()) {
                try {
                    CanonicalProductText canonicalText = canonicalProductTextBuilder.build(sourceProduct);
                    EmbeddingVector queryVector = embeddingService.generateEmbedding(canonicalText.getCanonicalText());

                    double minScore = effectiveRequest.getMinSemanticSimilarity() != null
                            ? effectiveRequest.getMinSemanticSimilarity()
                            : properties.getMinSimilarityScore();

                    SimilaritySearchRequest simReq = SimilaritySearchRequest.builder()
                            .queryVector(queryVector)
                            .entityType(ENTITY_TYPE_PRODUCT)
                            .modelName(embeddingService.getModelName())
                            .modelVersion(embeddingService.getModelVersion())
                            .topK(MAX_CANDIDATES_LIMIT)
                            .minScore(minScore)
                            .build();

                    List<SimilaritySearchResult> simResults = vectorStore.similaritySearch(simReq);
                    List<UUID> vectorIds = new ArrayList<>();
                    for (SimilaritySearchResult r : simResults) {
                        try {
                            UUID id = UUID.fromString(r.getEntityId());
                            if (!id.equals(productId)) {
                                vectorIds.add(id);
                                similarityScoresMap.put(id, r.getScore());
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (!vectorIds.isEmpty()) {
                        List<ProductEntity> vectorEntities = productRepository.findAllByIdInWithPricesAndSellers(vectorIds);
                        for (ProductEntity p : vectorEntities) {
                            if (p != null && p.getId() != null && !p.getId().equals(productId)) {
                                candidateEntityMap.put(p.getId(), p);
                            }
                        }
                    }
                } catch (Exception e) {
                    degradedCounter.increment();
                    log.warn("Vector search failed during alternative finding for product {}, degrading to structured: {}",
                            productId, e.getMessage());
                }
            }

            // B. Structured Taxonomy Retrieval (same category & brand)
            String targetCategory = (effectiveRequest.getCategory() != null && !effectiveRequest.getCategory().isBlank())
                    ? effectiveRequest.getCategory().trim()
                    : sourceProduct.getCategory();

            Specification<ProductEntity> structSpec = DiscoverySpecifications.buildDiscoverySpec(
                    Collections.emptyList(),
                    targetCategory,
                    effectiveRequest.getBrand(),
                    effectiveRequest.getMinPrice(),
                    effectiveRequest.getMaxPrice(),
                    effectiveRequest.getInStock(),
                    effectiveRequest.getSellerId()
            );

            Page<ProductEntity> structuredPage = productRepository.findAll(structSpec, PageRequest.of(0, MAX_CANDIDATES_LIMIT));
            for (ProductEntity p : structuredPage.getContent()) {
                if (p != null && p.getId() != null && !p.getId().equals(productId)) {
                    candidateEntityMap.put(p.getId(), p);
                    similarityScoresMap.putIfAbsent(p.getId(), 0.50); // Default baseline similarity for structured matches
                }
            }

            candidatesSummary.record(candidateEntityMap.size());

            if (candidateEntityMap.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(sourceContext, effectiveType, AlternativeMode.PRODUCT, null, duration);
            }

            // 3. Batch Price Loading for all Candidates
            List<UUID> candidateIds = new ArrayList<>(candidateEntityMap.keySet());
            List<ProductPriceEntity> candidatePrices = productPriceRepository.findPricesWithSellersByProductIds(candidateIds);
            Map<UUID, List<ProductPriceEntity>> pricesByProductId = candidatePrices.stream()
                    .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

            // 4. Candidate Scoring & Eligibility Evaluation
            List<AlternativeCandidate> scoredCandidates = new ArrayList<>();

            for (ProductEntity candidateEntity : candidateEntityMap.values()) {
                // Hard taxonomy & archival filter
                if (!passesTaxonomyAndArchivalFilters(candidateEntity, effectiveRequest.getCategory(), effectiveRequest.getBrand())) {
                    continue;
                }

                List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(candidateEntity.getId(), Collections.emptyList());

                BigDecimal bestPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getCurrentPrice)
                        .filter(Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal origPrice = prodPrices.stream()
                        .map(ProductPriceEntity::getOriginalPrice)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);

                BigDecimal maxDiscount = prodPrices.stream()
                        .map(ProductPriceEntity::getDiscountPercentage)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                // Hard price & stock filters
                if (!passesPriceAndStockFilters(bestPrice, prodPrices, effectiveRequest.getMinPrice(), effectiveRequest.getMaxPrice(),
                        effectiveRequest.getInStock(), effectiveRequest.getSellerId(), effectiveRequest.getMinDiscount())) {
                    continue;
                }

                Double similarity = similarityScoresMap.getOrDefault(candidateEntity.getId(), 0.50);

                AlternativeCandidate candidate = AlternativeCandidate.builder()
                        .product(candidateEntity)
                        .currentBestPrice(bestPrice)
                        .originalPrice(origPrice)
                        .discountPercentage(maxDiscount)
                        .prices(prodPrices)
                        .semanticSimilarityScore(similarity)
                        .build();

                // Check eligibility for requested alternative type
                if (!scoringStrategy.isEligible(candidate, sourceContext, effectiveType)) {
                    continue;
                }

                // Compute multi-dimensional score, evidence, and reason codes
                scoringStrategy.scoreCandidate(candidate, sourceContext, effectiveType);
                scoredCandidates.add(candidate);
            }

            if (scoredCandidates.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(sourceContext, effectiveType, AlternativeMode.PRODUCT, null, duration);
            }

            // 5. Deterministic Final Sorting
            scoredCandidates.sort(scoringStrategy.getDeterministicComparator());

            // 6. Slice Top-K
            List<AlternativeCandidate> topSlice = scoredCandidates.stream()
                    .limit(limit)
                    .toList();

            // 7. Enrich Top-K with Price Analytics
            List<AlternativeProductDTO> resultDTOs = enrichAndMapSlice(topSlice, sourceContext, effectiveType);
            resultsSummary.record(resultDTOs.size());

            // 8. Extract Facets
            Set<String> distinctCategories = new TreeSet<>();
            Set<String> distinctBrands = new TreeSet<>();
            for (AlternativeCandidate ac : scoredCandidates) {
                if (ac.getProduct().getCategory() != null) distinctCategories.add(ac.getProduct().getCategory());
                if (ac.getProduct().getBrand() != null) distinctBrands.add(ac.getProduct().getBrand());
            }

            long duration = System.currentTimeMillis() - start;
            searchTimer.record(duration, TimeUnit.MILLISECONDS);

            return AlternativeResponseDTO.builder()
                    .sourceProductContext(sourceContext)
                    .alternativeType(effectiveType)
                    .executionMode(AlternativeMode.PRODUCT)
                    .totalFound(scoredCandidates.size())
                    .content(resultDTOs)
                    .executionTimeMs(duration)
                    .availableCategories(new ArrayList<>(distinctCategories))
                    .availableBrands(new ArrayList<>(distinctBrands))
                    .appliedNotes(List.of(String.format("Found %d %s alternatives for '%s'",
                            scoredCandidates.size(), effectiveType, sourceProduct.getName())))
                    .build();

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to find alternatives for product {}", productId, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findAlternativesForQuery(AlternativeRequest request) {
        Objects.requireNonNull(request, "AlternativeRequest cannot be null");
        requestCounter.increment();
        long start = System.currentTimeMillis();

        String rawQuery = request.getQuery() != null ? request.getQuery().trim() : "";
        AlternativeType effectiveType = request.getType() != null ? request.getType() : AlternativeType.SIMILAR;
        int limit = Math.min(MAX_RESULTS_LIMIT, request.getEffectiveLimit());

        try {
            // 1. Interpret Natural Language Query
            ShoppingQueryIntent intent = shoppingQueryInterpreter.interpret(rawQuery);
            ShoppingQueryValidationResult validation = intentValidator.validate(intent);

            if (validation.isHasConflicts()) {
                long duration = System.currentTimeMillis() - start;
                return AlternativeResponseDTO.builder()
                        .alternativeType(effectiveType)
                        .executionMode(AlternativeMode.QUERY)
                        .query(rawQuery)
                        .interpretedIntent(intent)
                        .totalFound(0)
                        .content(Collections.emptyList())
                        .executionTimeMs(duration)
                        .appliedNotes(validation.getErrors())
                        .build();
            }

            // 2. Map Intent to HybridSearchRequest
            HybridSearchRequest hybridRequest = intentValidator.toHybridSearchRequest(intent, 0, MAX_CANDIDATES_LIMIT, "relevance");
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                hybridRequest.setCategory(request.getCategory().trim());
            }
            if (request.getBrand() != null && !request.getBrand().isBlank()) {
                hybridRequest.setBrand(request.getBrand().trim());
            }
            if (request.getMinPrice() != null) {
                hybridRequest.setMinPrice(request.getMinPrice());
            }
            if (request.getMaxPrice() != null) {
                hybridRequest.setMaxPrice(request.getMaxPrice());
            }

            // 3. Execute Candidate Search via Hybrid Search
            DiscoverySearchResponseDTO discoveryResponse = hybridSearchService.search(hybridRequest);
            List<UUID> productIds = discoveryResponse.getContent().stream()
                    .map(com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO::getId)
                    .toList();

            if (productIds.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(null, effectiveType, AlternativeMode.QUERY, intent, duration);
            }

            List<ProductEntity> entities = productRepository.findAllByIdInWithPricesAndSellers(productIds);
            Map<UUID, ProductEntity> entityMap = entities.stream()
                    .collect(Collectors.toMap(ProductEntity::getId, p -> p, (p1, p2) -> p1));

            List<ProductPriceEntity> candidatePrices = productPriceRepository.findPricesWithSellersByProductIds(productIds);
            Map<UUID, List<ProductPriceEntity>> pricesByProductId = candidatePrices.stream()
                    .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

            // Determine baseline: first result or synthesized baseline from query
            SourceProductContextDTO synthesizedBaseline = null;
            if (intent.getMaxPrice() != null) {
                synthesizedBaseline = SourceProductContextDTO.builder()
                        .name("Target Query Intent (" + rawQuery + ")")
                        .category(intent.getCategory())
                        .brand(intent.getBrand())
                        .currentBestPrice(intent.getMaxPrice())
                        .rating(intent.getMinRating() != null ? intent.getMinRating() : 4.0)
                        .build();
            } else if (!discoveryResponse.getContent().isEmpty()) {
                var first = discoveryResponse.getContent().get(0);
                synthesizedBaseline = SourceProductContextDTO.builder()
                        .id(first.getId())
                        .name(first.getName())
                        .category(first.getCategory())
                        .brand(first.getBrand())
                        .currentBestPrice(first.getCurrentBestPrice())
                        .originalPrice(first.getOriginalPrice())
                        .discountPercentage(first.getDiscountPercentage())
                        .rating(4.5)
                        .dealQuality(first.getDealQuality())
                        .build();
            }

            // 4. Score & Rank Alternatives
            List<AlternativeCandidate> scoredCandidates = new ArrayList<>();

            for (com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO discProduct : discoveryResponse.getContent()) {
                ProductEntity entity = entityMap.get(discProduct.getId());
                if (entity == null || entity.isArchived()) {
                    continue;
                }

                List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(entity.getId(), Collections.emptyList());

                double relevance = discProduct.getRelevanceScore() != null ? discProduct.getRelevanceScore() : 0.0;

                AlternativeCandidate candidate = AlternativeCandidate.builder()
                        .product(entity)
                        .currentBestPrice(discProduct.getCurrentBestPrice())
                        .originalPrice(discProduct.getOriginalPrice())
                        .discountPercentage(discProduct.getDiscountPercentage())
                        .prices(prodPrices)
                        .semanticSimilarityScore(0.80) // High confidence from hybrid discovery
                        .relevanceScore(relevance)
                        .dealQuality(discProduct.getDealQuality())
                        .priceTrend(discProduct.getPriceTrend())
                        .purchaseSignal(discProduct.getPurchaseSignal())
                        .isHistoricalLow(discProduct.getIsHistoricalLow())
                        .build();

                if (scoringStrategy.isEligible(candidate, synthesizedBaseline, effectiveType)) {
                    scoringStrategy.scoreCandidate(candidate, synthesizedBaseline, effectiveType);
                    scoredCandidates.add(candidate);
                }
            }

            scoredCandidates.sort(scoringStrategy.getDeterministicComparator());

            List<AlternativeCandidate> topSlice = scoredCandidates.stream()
                    .limit(limit)
                    .toList();

            List<AlternativeProductDTO> resultDTOs = enrichAndMapSlice(topSlice, synthesizedBaseline, effectiveType);

            long duration = System.currentTimeMillis() - start;
            searchTimer.record(duration, TimeUnit.MILLISECONDS);

            return AlternativeResponseDTO.builder()
                    .sourceProductContext(synthesizedBaseline)
                    .alternativeType(effectiveType)
                    .executionMode(AlternativeMode.QUERY)
                    .query(rawQuery)
                    .interpretedIntent(intent)
                    .totalFound(scoredCandidates.size())
                    .content(resultDTOs)
                    .executionTimeMs(duration)
                    .availableCategories(discoveryResponse.getAvailableCategories())
                    .availableBrands(discoveryResponse.getAvailableBrands())
                    .appliedNotes(List.of(String.format("Discovered %d %s alternatives for query '%s'",
                            scoredCandidates.size(), effectiveType, rawQuery)))
                    .build();

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to find alternatives for query '{}'", rawQuery, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findAlternativesForNaturalLanguage(String query, AlternativeType type, int limit) {
        String effectiveQuery = query != null ? query.trim() : "";
        AlternativeType detectedType = type;

        // Auto-detect AlternativeType from query keywords if not explicitly specified
        if (detectedType == null || detectedType == AlternativeType.SIMILAR) {
            String lower = effectiveQuery.toLowerCase();
            if (lower.contains("cheaper") || lower.contains("affordable") || lower.contains("low price") || lower.contains("save money") || lower.contains("less expensive")) {
                detectedType = AlternativeType.CHEAPER;
            } else if (lower.contains("budget") || lower.contains("fallback") || lower.contains("economy")) {
                detectedType = AlternativeType.BUDGET_FALLBACK;
            } else if (lower.contains("better value") || lower.contains("best value") || lower.contains("good deal") || lower.contains("bang for buck")) {
                detectedType = AlternativeType.BETTER_VALUE;
            } else if (lower.contains("upgrade") || lower.contains("higher performance") || lower.contains("better than") || lower.contains("faster")) {
                detectedType = AlternativeType.PERFORMANCE_UPGRADE;
            } else if (lower.contains("premium") || lower.contains("high end") || lower.contains("luxury") || lower.contains("flagship")) {
                detectedType = AlternativeType.PREMIUM;
            } else {
                detectedType = AlternativeType.SIMILAR;
            }
        }

        AlternativeRequest request = AlternativeRequest.builder()
                .query(effectiveQuery)
                .type(detectedType)
                .limit(limit > 0 ? limit : 10)
                .build();

        return findAlternativesForQuery(request);
    }

    private List<AlternativeProductDTO> enrichAndMapSlice(
            List<AlternativeCandidate> slice,
            SourceProductContextDTO sourceContext,
            AlternativeType type) {

        List<AlternativeProductDTO> results = new ArrayList<>();

        for (int i = 0; i < slice.size(); i++) {
            AlternativeCandidate cand = slice.get(i);
            ProductEntity entity = cand.getProduct();

            // Sliced price analytics enrichment
            try {
                ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(entity.getId());
                if (analytics != null) {
                    cand.setDealQuality(analytics.getDealQuality());
                    cand.setPriceTrend(analytics.getTrend());
                    cand.setPurchaseSignal(analytics.getPurchaseSignal());
                    if (analytics.getHistoricalMin() != null && cand.getCurrentBestPrice() != null
                            && cand.getCurrentBestPrice().compareTo(analytics.getHistoricalMin()) <= 0) {
                        cand.setIsHistoricalLow(true);
                    }
                    // Re-score to ensure analytics signals boost badge list
                    scoringStrategy.scoreCandidate(cand, sourceContext, type);
                }
            } catch (Exception e) {
                log.warn("Price analytics unavailable for alternative candidate {}: {}", entity.getId(), e.getMessage());
            }

            List<ProductPriceSearchResultDTO> priceDTOs = cand.getPrices().stream()
                    .map(ProductPriceSearchResultDTO::fromEntity)
                    .collect(Collectors.toList());

            List<String> badges = new ArrayList<>(cand.getBadges());
            if (i == 0 && cand.getAlternativeScore() >= 40.0) {
                badges.add(0, "Top Alternative");
            }

            AlternativeProductDTO dto = AlternativeProductDTO.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .brand(entity.getBrand())
                    .category(entity.getCategory())
                    .description(entity.getDescription())
                    .imageUrl(entity.getImageUrl())
                    .archived(entity.isArchived())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .currentBestPrice(cand.getCurrentBestPrice())
                    .originalPrice(cand.getOriginalPrice())
                    .discountPercentage(cand.getDiscountPercentage())
                    .prices(priceDTOs)
                    .inStock(!cand.getPrices().isEmpty())
                    .alternativeScore(cand.getAlternativeScore())
                    .semanticSimilarityScore(cand.getSemanticSimilarityScore())
                    .relevanceScore(cand.getRelevanceScore())
                    .rating(cand.getRating())
                    .priceDifference(cand.getPriceDifference())
                    .priceDifferencePercentage(cand.getPriceDifferencePercentage())
                    .dealQuality(cand.getDealQuality())
                    .priceTrend(cand.getPriceTrend())
                    .purchaseSignal(cand.getPurchaseSignal())
                    .isHistoricalLow(cand.getIsHistoricalLow())
                    .badges(badges)
                    .reasonCodes(cand.getReasonCodes())
                    .evidence(cand.getEvidence())
                    .primaryExplanation(cand.getPrimaryExplanation())
                    .build();

            results.add(dto);
        }

        return results;
    }

    private boolean passesTaxonomyAndArchivalFilters(ProductEntity p, String effectiveCategory, String effectiveBrand) {
        if (p == null || p.isArchived()) {
            return false;
        }
        if (effectiveCategory != null && !effectiveCategory.isBlank() && !"All".equalsIgnoreCase(effectiveCategory)) {
            if (p.getCategory() == null || !p.getCategory().equalsIgnoreCase(effectiveCategory.trim())) {
                return false;
            }
        }
        if (effectiveBrand != null && !effectiveBrand.isBlank() && !"All".equalsIgnoreCase(effectiveBrand)) {
            if (p.getBrand() == null || !p.getBrand().equalsIgnoreCase(effectiveBrand.trim())) {
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

    private Double calculateDeterministicRating(UUID id) {
        if (id == null) return 4.0;
        int hash = Math.abs(id.hashCode());
        return Math.round((4.0 + ((hash % 11) / 10.0)) * 10.0) / 10.0;
    }

    private AlternativeResponseDTO buildEmptyResponse(
            SourceProductContextDTO sourceContext,
            AlternativeType type,
            AlternativeMode mode,
            ShoppingQueryIntent intent,
            long duration) {

        List<String> categories = Collections.emptyList();
        List<String> brands = Collections.emptyList();
        try {
            List<String> repoCats = productRepository.findDistinctCategories();
            if (repoCats != null) categories = repoCats;
            List<String> repoBrands = productRepository.findDistinctBrands();
            if (repoBrands != null) brands = repoBrands;
        } catch (Exception ignored) {
        }

        return AlternativeResponseDTO.builder()
                .sourceProductContext(sourceContext)
                .alternativeType(type)
                .executionMode(mode)
                .interpretedIntent(intent)
                .totalFound(0)
                .content(Collections.emptyList())
                .executionTimeMs(duration)
                .availableCategories(categories)
                .availableBrands(brands)
                .appliedNotes(List.of("No matching alternatives found matching constraints"))
                .build();
    }
}
