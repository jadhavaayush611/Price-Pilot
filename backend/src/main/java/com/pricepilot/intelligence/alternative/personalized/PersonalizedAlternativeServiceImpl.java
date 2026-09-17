package com.pricepilot.intelligence.alternative.personalized;

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
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidence;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScore;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScoringStrategy;
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
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link PersonalizedAlternativeService}.
 * <p>
 * Orchestrates candidate retrieval, hard-constraint filtering, authoritative qualification,
 * deterministic base alternative scoring, single-context personalization scoring,
 * deterministic multi-tier ranking, and grounded explainability generation.
 * <p>
 * Core Invariant:
 * "Personalization may change the ranking among valid alternatives, but it must never make
 * an invalid alternative valid or bypass hard eligibility rules."
 */
@Service
public class PersonalizedAlternativeServiceImpl implements PersonalizedAlternativeService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizedAlternativeServiceImpl.class);
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
    private final PersonalizationContextProvider personalizationContextProvider;
    private final PersonalizedScoringStrategy personalizedScoringStrategy;
    private final PersonalizedEvidenceGenerator personalizedEvidenceGenerator;

    // Observability Metrics
    private final Counter requestCounter;
    private final Counter failureCounter;
    private final Counter fallbackCounter;
    private final Counter degradedCounter;
    private final Timer searchTimer;
    private final DistributionSummary candidatesSummary;
    private final DistributionSummary resultsSummary;

    public PersonalizedAlternativeServiceImpl(
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
            PersonalizationContextProvider personalizationContextProvider,
            PersonalizedScoringStrategy personalizedScoringStrategy,
            PersonalizedEvidenceGenerator personalizedEvidenceGenerator,
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
        this.personalizationContextProvider = Objects.requireNonNull(personalizationContextProvider, "PersonalizationContextProvider cannot be null");
        this.personalizedScoringStrategy = Objects.requireNonNull(personalizedScoringStrategy, "PersonalizedScoringStrategy cannot be null");
        this.personalizedEvidenceGenerator = Objects.requireNonNull(personalizedEvidenceGenerator, "PersonalizedEvidenceGenerator cannot be null");

        this.requestCounter = Counter.builder("pricepilot.alternative.personalized.requests")
                .description("Total personalized alternative discovery requests")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.alternative.personalized.failures")
                .description("Total personalized alternative discovery failures")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("pricepilot.alternative.personalized.fallbacks")
                .description("Personalization context fallbacks due to provider failure")
                .register(meterRegistry);
        this.degradedCounter = Counter.builder("pricepilot.alternative.personalized.degraded")
                .description("Personalized alternative searches degraded to structured-only")
                .register(meterRegistry);
        this.searchTimer = Timer.builder("pricepilot.alternative.personalized.latency")
                .description("Latency of personalized alternative discovery operations")
                .register(meterRegistry);
        this.candidatesSummary = DistributionSummary.builder("pricepilot.alternative.personalized.candidates")
                .description("Number of alternative candidates retrieved")
                .register(meterRegistry);
        this.resultsSummary = DistributionSummary.builder("pricepilot.alternative.personalized.results")
                .description("Number of personalized alternatives returned to client")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForProduct(UUID productId, AlternativeRequest request, UUID userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

        PersonalizationContext context = resolveContext(userId);
        return findPersonalizedAlternativesForProduct(productId, request, context);
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForProduct(UUID productId, AlternativeRequest request, PersonalizationContext context) {
        Objects.requireNonNull(productId, "Product ID cannot be null");
        requestCounter.increment();
        long start = System.currentTimeMillis();

        AlternativeRequest effectiveRequest = request != null ? request : AlternativeRequest.builder().productId(productId).build();
        AlternativeType effectiveType = effectiveRequest.getType() != null ? effectiveRequest.getType() : AlternativeType.SIMILAR;
        int limit = Math.min(MAX_RESULTS_LIMIT, effectiveRequest.getEffectiveLimit());

        try {
            // 1. Fetch Source Product Context
            ProductEntity sourceProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

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
                    similarityScoresMap.putIfAbsent(p.getId(), 0.50);
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

            // 4. Candidate Qualification & Base Scoring
            List<PersonalizedCandidateHolder> scoredCandidates = new ArrayList<>();

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

                // Authoritative Eligibility Check (Existing Qualification)
                if (!scoringStrategy.isEligible(candidate, sourceContext, effectiveType)) {
                    continue;
                }

                // Compute deterministic base alternative score
                scoringStrategy.scoreCandidate(candidate, sourceContext, effectiveType);

                // Convert candidate to ProductResponseDTO for Phase 6.4 Personalized Scoring
                ProductResponseDTO productDto = toProductResponseDTO(candidateEntity, prodPrices);

                // Compute bounded personalized score
                PersonalizedScore pScore = personalizedScoringStrategy.score(productDto, candidate.getAlternativeScore(), context);

                scoredCandidates.add(new PersonalizedCandidateHolder(candidate, productDto, pScore, similarity));
            }

            if (scoredCandidates.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(sourceContext, effectiveType, AlternativeMode.PRODUCT, null, duration);
            }

            // 5. Deterministic Multi-Tier Ranking
            scoredCandidates.sort(getPersonalizedComparator());

            // 6. Slice Top-K
            List<PersonalizedCandidateHolder> topSlice = scoredCandidates.stream()
                    .limit(limit)
                    .toList();

            // 7. Enrich Top-K with Price Analytics and Grounded Personalized Evidence
            List<AlternativeProductDTO> resultDTOs = enrichAndMapSlice(topSlice, sourceContext, effectiveType, context);
            resultsSummary.record(resultDTOs.size());

            // 8. Extract Facets
            Set<String> distinctCategories = new TreeSet<>();
            Set<String> distinctBrands = new TreeSet<>();
            for (PersonalizedCandidateHolder holder : scoredCandidates) {
                if (holder.candidate().getProduct().getCategory() != null) distinctCategories.add(holder.candidate().getProduct().getCategory());
                if (holder.candidate().getProduct().getBrand() != null) distinctBrands.add(holder.candidate().getProduct().getBrand());
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
                    .appliedNotes(List.of(String.format("Found %d personalized %s alternatives for '%s'",
                            scoredCandidates.size(), effectiveType, sourceProduct.getName())))
                    .build();

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to find personalized alternatives for product {}", productId, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForQuery(AlternativeRequest request, UUID userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

        PersonalizationContext context = resolveContext(userId);
        return findPersonalizedAlternativesForQuery(request, context);
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForQuery(AlternativeRequest request, PersonalizationContext context) {
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

            // Determine baseline
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

            // 4. Candidate Qualification & Base Scoring
            List<PersonalizedCandidateHolder> scoredCandidates = new ArrayList<>();

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
                        .semanticSimilarityScore(0.80)
                        .relevanceScore(relevance)
                        .dealQuality(discProduct.getDealQuality())
                        .priceTrend(discProduct.getPriceTrend())
                        .purchaseSignal(discProduct.getPurchaseSignal())
                        .isHistoricalLow(discProduct.getIsHistoricalLow())
                        .build();

                // Authoritative Eligibility Check
                if (!scoringStrategy.isEligible(candidate, synthesizedBaseline, effectiveType)) {
                    continue;
                }

                // Base scoring
                scoringStrategy.scoreCandidate(candidate, synthesizedBaseline, effectiveType);

                // Personalized scoring
                ProductResponseDTO productDto = toProductResponseDTO(entity, prodPrices);
                PersonalizedScore pScore = personalizedScoringStrategy.score(productDto, candidate.getAlternativeScore(), context);

                scoredCandidates.add(new PersonalizedCandidateHolder(candidate, productDto, pScore, 0.80));
            }

            if (scoredCandidates.isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                return buildEmptyResponse(synthesizedBaseline, effectiveType, AlternativeMode.QUERY, intent, duration);
            }

            // 5. Deterministic Sort
            scoredCandidates.sort(getPersonalizedComparator());

            // 6. Slice Top-K
            List<PersonalizedCandidateHolder> topSlice = scoredCandidates.stream()
                    .limit(limit)
                    .toList();

            // 7. Enrich Slice
            List<AlternativeProductDTO> resultDTOs = enrichAndMapSlice(topSlice, synthesizedBaseline, effectiveType, context);

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
                    .appliedNotes(List.of(String.format("Discovered %d personalized %s alternatives for query '%s'",
                            scoredCandidates.size(), effectiveType, rawQuery)))
                    .build();

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to find personalized alternatives for query '{}'", rawQuery, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForNaturalLanguage(String query, AlternativeType type, int limit, UUID userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

        PersonalizationContext context = resolveContext(userId);
        return findPersonalizedAlternativesForNaturalLanguage(query, type, limit, context);
    }

    @Override
    @Transactional(readOnly = true)
    public AlternativeResponseDTO findPersonalizedAlternativesForNaturalLanguage(String query, AlternativeType type, int limit, PersonalizationContext context) {
        String effectiveQuery = query != null ? query.trim() : "";
        AlternativeType detectedType = type;

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
                .personalized(true)
                .build();

        return findPersonalizedAlternativesForQuery(request, context);
    }

    private PersonalizationContext resolveContext(UUID userId) {
        try {
            return personalizationContextProvider.getPersonalizationContext(userId);
        } catch (AccessDeniedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Personalization context provider failed for user: {}. Falling back to empty context. Cause: {}",
                    userId, e.getMessage());
            fallbackCounter.increment();
            return PersonalizationContext.empty(userId);
        }
    }

    private List<AlternativeProductDTO> enrichAndMapSlice(
            List<PersonalizedCandidateHolder> slice,
            SourceProductContextDTO sourceContext,
            AlternativeType type,
            PersonalizationContext context) {

        List<AlternativeProductDTO> results = new ArrayList<>();

        for (int i = 0; i < slice.size(); i++) {
            PersonalizedCandidateHolder holder = slice.get(i);
            AlternativeCandidate cand = holder.candidate();
            ProductEntity entity = cand.getProduct();
            ProductResponseDTO productDto = holder.productDto();
            PersonalizedScore pScore = holder.personalizedScore();

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
                    // Re-score to ensure analytics signals update badge list
                    scoringStrategy.scoreCandidate(cand, sourceContext, type);
                }
            } catch (Exception e) {
                log.warn("Price analytics unavailable for alternative candidate {}: {}", entity.getId(), e.getMessage());
            }

            // Generate Grounded Personalized Evidence (Phase 6.5)
            PersonalizedEvidence personalizedEvidence = personalizedEvidenceGenerator.generate(productDto, pScore, context);

            List<ProductPriceSearchResultDTO> priceDTOs = cand.getPrices().stream()
                    .map(ProductPriceSearchResultDTO::fromEntity)
                    .collect(Collectors.toList());

            List<String> badges = new ArrayList<>(cand.getBadges());
            if (personalizedEvidence.hasPersonalization()) {
                badges.add(0, "Personalized Match");
            }
            if (i == 0 && (pScore.getFinalScore() >= 40.0 || cand.getAlternativeScore() >= 40.0)) {
                if (!badges.contains("Top Alternative")) {
                    badges.add(0, "Top Alternative");
                }
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
                    .personalizedScore(pScore.getFinalScore())
                    .personalizationAdjustment(pScore.getPersonalizationAdjustment())
                    .personalizedEvidence(personalizedEvidence)
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

    private Comparator<PersonalizedCandidateHolder> getPersonalizedComparator() {
        return (c1, c2) -> {
            PersonalizedScore s1 = c1.personalizedScore();
            PersonalizedScore s2 = c2.personalizedScore();

            // Tier 1: Final personalized score descending
            int comp = Double.compare(s2.getFinalScore(), s1.getFinalScore());
            if (comp != 0) return comp;

            // Tier 2: Generic alternative base score descending
            comp = Double.compare(s2.getBaseScore(), s1.getBaseScore());
            if (comp != 0) return comp;

            // Tier 3: Personalization adjustment descending
            comp = Double.compare(s2.getPersonalizationAdjustment(), s1.getPersonalizationAdjustment());
            if (comp != 0) return comp;

            // Tier 4: Semantic similarity descending
            double sim1 = c1.semanticSimilarity() != null ? c1.semanticSimilarity() : 0.0;
            double sim2 = c2.semanticSimilarity() != null ? c2.semanticSimilarity() : 0.0;
            comp = Double.compare(sim2, sim1);
            if (comp != 0) return comp;

            // Tier 5: Deal quality descending
            int dq1 = getDealQualityRank(c1.candidate().getDealQuality());
            int dq2 = getDealQualityRank(c2.candidate().getDealQuality());
            comp = Integer.compare(dq2, dq1);
            if (comp != 0) return comp;

            // Tier 6: Rating descending
            double r1 = c1.candidate().getRating() != null ? c1.candidate().getRating() : 0.0;
            double r2 = c2.candidate().getRating() != null ? c2.candidate().getRating() : 0.0;
            comp = Double.compare(r2, r1);
            if (comp != 0) return comp;

            // Tier 7: Current price ascending
            BigDecimal p1 = c1.candidate().getCurrentBestPrice() != null ? c1.candidate().getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal p2 = c2.candidate().getCurrentBestPrice() != null ? c2.candidate().getCurrentBestPrice() : BigDecimal.valueOf(Double.MAX_VALUE);
            comp = p1.compareTo(p2);
            if (comp != 0) return comp;

            // Tier 8: Product UUID lexicographical ascending
            String id1 = c1.candidate().getProductId() != null ? c1.candidate().getProductId().toString() : "";
            String id2 = c2.candidate().getProductId() != null ? c2.candidate().getProductId().toString() : "";
            return id1.compareTo(id2);
        };
    }

    private int getDealQualityRank(DealQuality quality) {
        if (quality == null) return 0;
        return switch (quality) {
            case EXCELLENT_DEAL -> 5;
            case GOOD_DEAL -> 4;
            case FAIR_PRICE -> 3;
            case ABOVE_AVERAGE -> 2;
            case HIGH_PRICE -> 1;
            case INSUFFICIENT_DATA -> 0;
        };
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

    private ProductResponseDTO toProductResponseDTO(ProductEntity product, List<ProductPriceEntity> prodPrices) {
        List<ProductPriceResponseDTO> priceDTOs = prodPrices.stream()
                .map(p -> ProductPriceResponseDTO.builder()
                        .id(p.getId())
                        .currentPrice(p.getCurrentPrice())
                        .originalPrice(p.getOriginalPrice())
                        .discountPercentage(p.getDiscountPercentage())
                        .productUrl(p.getProductUrl())
                        .seller(p.getSeller() != null ? SellerResponseDTO.fromEntity(p.getSeller()) : null)
                        .build())
                .toList();

        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .archived(product.isArchived())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .prices(priceDTOs)
                .build();
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
                .appliedNotes(List.of("No matching personalized alternatives found matching constraints"))
                .build();
    }

    private record PersonalizedCandidateHolder(
            AlternativeCandidate candidate,
            ProductResponseDTO productDto,
            PersonalizedScore personalizedScore,
            Double semanticSimilarity
    ) {}
}
