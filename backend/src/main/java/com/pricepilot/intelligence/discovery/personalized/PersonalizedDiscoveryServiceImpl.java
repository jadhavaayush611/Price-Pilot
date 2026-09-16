package com.pricepilot.intelligence.discovery.personalized;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.*;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
import com.pricepilot.intelligence.discovery.specification.DiscoverySpecifications;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidence;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScore;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScoringStrategy;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link PersonalizedDiscoveryService}.
 * <p>
 * Orchestrates candidate retrieval, strict hard-constraint filtering, deterministic base scoring,
 * single-context loading, Phase 6.4 personalized scoring, deterministic multi-tier ranking,
 * and Phase 6.5 grounded evidence enrichment.
 */
@Service
public class PersonalizedDiscoveryServiceImpl implements PersonalizedDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizedDiscoveryServiceImpl.class);
    private static final int MAX_CANDIDATE_POOL = 150;
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final QueryInterpreter queryInterpreter;
    private final DefaultSearchRelevanceScorer relevanceScorer;
    private final PersonalizationContextProvider personalizationContextProvider;
    private final PersonalizedScoringStrategy personalizedScoringStrategy;
    private final PersonalizedEvidenceGenerator personalizedEvidenceGenerator;

    // Metrics
    private final Counter personalizedSearchCounter;
    private final Counter fallbackCounter;
    private final Timer personalizedLatencyTimer;

    public PersonalizedDiscoveryServiceImpl(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            PriceAnalyticsService priceAnalyticsService,
            QueryInterpreter queryInterpreter,
            DefaultSearchRelevanceScorer relevanceScorer,
            PersonalizationContextProvider personalizationContextProvider,
            PersonalizedScoringStrategy personalizedScoringStrategy,
            PersonalizedEvidenceGenerator personalizedEvidenceGenerator,
            MeterRegistry meterRegistry) {

        this.productRepository = Objects.requireNonNull(productRepository, "ProductRepository cannot be null");
        this.productPriceRepository = Objects.requireNonNull(productPriceRepository, "ProductPriceRepository cannot be null");
        this.priceAnalyticsService = Objects.requireNonNull(priceAnalyticsService, "PriceAnalyticsService cannot be null");
        this.queryInterpreter = Objects.requireNonNull(queryInterpreter, "QueryInterpreter cannot be null");
        this.relevanceScorer = Objects.requireNonNull(relevanceScorer, "DefaultSearchRelevanceScorer cannot be null");
        this.personalizationContextProvider = Objects.requireNonNull(personalizationContextProvider, "PersonalizationContextProvider cannot be null");
        this.personalizedScoringStrategy = Objects.requireNonNull(personalizedScoringStrategy, "PersonalizedScoringStrategy cannot be null");
        this.personalizedEvidenceGenerator = Objects.requireNonNull(personalizedEvidenceGenerator, "PersonalizedEvidenceGenerator cannot be null");

        this.personalizedSearchCounter = Counter.builder("pricepilot.discovery.personalized.requests")
                .description("Total personalized discovery requests")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("pricepilot.discovery.personalized.fallbacks")
                .description("Personalization context fallbacks due to provider failure")
                .register(meterRegistry);
        this.personalizedLatencyTimer = Timer.builder("pricepilot.discovery.personalized.latency")
                .description("Personalized discovery search latency")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverySearchResponseDTO discover(DiscoverySearchRequestDTO request, UUID userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required for personalized product discovery");
        }

        PersonalizationContext context;
        try {
            context = personalizationContextProvider.getPersonalizationContext(userId);
        } catch (AccessDeniedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Personalization context provider failed for user: {}. Falling back to empty context. Cause: {}",
                    userId, e.getMessage());
            fallbackCounter.increment();
            context = PersonalizationContext.empty(userId);
        }

        return discover(request, context);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverySearchResponseDTO discover(DiscoverySearchRequestDTO request, PersonalizationContext context) {
        personalizedSearchCounter.increment();
        long start = System.currentTimeMillis();

        DiscoverySearchRequestDTO req = (request != null) ? request : DiscoverySearchRequestDTO.builder().build();

        // 1. Sanitize pagination parameters
        int page = Math.max(0, req.getPage());
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, req.getSize() > 0 ? req.getSize() : 10));

        // 2. Interpret search query
        String rawQuery = req.getQuery() != null ? req.getQuery() : "";
        InterpretedQuery interpreted = queryInterpreter.interpret(rawQuery);

        // 3. Resolve effective filter parameters (Hard constraints)
        String effectiveCategory = (req.getCategory() != null && !req.getCategory().isBlank() && !"All".equalsIgnoreCase(req.getCategory()))
                ? req.getCategory().trim()
                : interpreted.getDetectedCategory();

        String effectiveBrand = (req.getBrand() != null && !req.getBrand().isBlank() && !"All".equalsIgnoreCase(req.getBrand()))
                ? req.getBrand().trim()
                : interpreted.getDetectedBrand();

        BigDecimal effectiveMinPrice = req.getMinPrice() != null ? req.getMinPrice() : interpreted.getMinPrice();
        BigDecimal effectiveMaxPrice = req.getMaxPrice() != null ? req.getMaxPrice() : interpreted.getMaxPrice();
        Boolean effectiveInStock = req.getInStock() != null ? req.getInStock() : interpreted.getInStockOnly();

        // 4. Build database-side query Specification enforcing HARD FILTERS FIRST
        List<String> tokens = interpreted.getSearchTokens();
        Specification<ProductEntity> spec = DiscoverySpecifications.buildDiscoverySpec(
                tokens,
                effectiveCategory,
                effectiveBrand,
                effectiveMinPrice,
                effectiveMaxPrice,
                effectiveInStock,
                req.getSellerId()
        );

        // 5. Bounded Candidate Retrieval: fetch candidate pool under hard constraints
        int candidatePoolLimit = Math.min(MAX_CANDIDATE_POOL, Math.max(50, (page + 1) * size * 2));
        Pageable candidatePageRequest = PageRequest.of(0, candidatePoolLimit);
        Page<ProductEntity> candidatePage = productRepository.findAll(spec, candidatePageRequest);
        long totalElements = candidatePage.getTotalElements();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        if (candidatePage.isEmpty()) {
            long duration = System.currentTimeMillis() - start;
            return DiscoverySearchResponseDTO.builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .interpretedQuery(interpreted)
                    .appliedSort(req.getSort() != null ? req.getSort() : "relevance")
                    .executionTimeMs(duration)
                    .availableCategories(productRepository.findDistinctCategories())
                    .availableBrands(productRepository.findDistinctBrands())
                    .build();
        }

        List<ProductEntity> candidateProducts = candidatePage.getContent();
        List<UUID> candidateIds = candidateProducts.stream().map(ProductEntity::getId).toList();

        // 6. Batch load prices for candidate products (No N+1 queries)
        List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(candidateIds);
        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        // 7. Base Relevance Scoring
        List<PersonalizedCandidateHolder> candidateHolders = new ArrayList<>();
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

            ScoredProductCandidate scoredCandidate = ScoredProductCandidate.builder()
                    .product(product)
                    .currentBestPrice(bestPrice)
                    .originalPrice(originalPrice)
                    .discountPercentage(maxDiscount)
                    .build();

            relevanceScorer.scoreCandidate(scoredCandidate, interpreted);
            double baseScore = scoredCandidate.getRelevanceScore();

            // Convert to ProductResponseDTO for personalization engine
            ProductResponseDTO productDto = toProductResponseDTO(product, prodPrices);

            // 8. Personalized Scoring using Phase 6.4 Strategy
            PersonalizedScore personalizedScore = personalizedScoringStrategy.score(productDto, baseScore, context);

            candidateHolders.add(new PersonalizedCandidateHolder(product, productDto, scoredCandidate, personalizedScore, bestPrice));
        }

        // 9. Deterministic Multi-Tier Ranking
        String sortMode = req.getSort() != null ? req.getSort().trim().toLowerCase() : "relevance";
        if ("relevance".equalsIgnoreCase(sortMode) || "default".equalsIgnoreCase(sortMode) || sortMode.isEmpty()) {
            candidateHolders.sort((c1, c2) -> {
                PersonalizedScore s1 = c1.personalizedScore();
                PersonalizedScore s2 = c2.personalizedScore();

                // Tier 1: Final Score descending
                int cmpFinal = Double.compare(s2.getFinalScore(), s1.getFinalScore());
                if (cmpFinal != 0) return cmpFinal;

                // Tier 2: Base Score descending
                int cmpBase = Double.compare(s2.getBaseScore(), s1.getBaseScore());
                if (cmpBase != 0) return cmpBase;

                // Tier 3: Personalization Adjustment descending
                int cmpAdj = Double.compare(s2.getPersonalizationAdjustment(), s1.getPersonalizationAdjustment());
                if (cmpAdj != 0) return cmpAdj;

                // Tier 4: Lowest Price ascending
                BigDecimal pr1 = c1.bestPrice();
                BigDecimal pr2 = c2.bestPrice();
                if (pr1 != null && pr2 != null) {
                    int cmpPrice = pr1.compareTo(pr2);
                    if (cmpPrice != 0) return cmpPrice;
                } else if (pr1 != null) {
                    return -1;
                } else if (pr2 != null) {
                    return 1;
                }

                // Tier 5: Lexicographical Product UUID ascending
                return c1.product().getId().compareTo(c2.product().getId());
            });
        } else {
            // Apply explicit requested sort
            applyExplicitSort(candidateHolders, sortMode);
        }

        // 10. Pagination Slicing
        int fromIndex = page * size;
        List<PersonalizedCandidateHolder> pagedSlice;
        if (fromIndex >= candidateHolders.size()) {
            pagedSlice = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + size, candidateHolders.size());
            pagedSlice = candidateHolders.subList(fromIndex, toIndex);
        }

        // 11. Bounded Phase 6.5 Evidence & Phase 4 Analytics Enrichment (ONLY on paged slice)
        List<DiscoveryProductDTO> discoveryProducts = new ArrayList<>();
        boolean isFirstPage = (page == 0);

        for (int i = 0; i < pagedSlice.size(); i++) {
            PersonalizedCandidateHolder holder = pagedSlice.get(i);
            ProductEntity product = holder.product();
            ProductResponseDTO productDto = holder.productDto();
            PersonalizedScore pScore = holder.personalizedScore();
            ScoredProductCandidate scoredCand = holder.scoredCandidate();
            List<ProductPriceEntity> prodPrices = pricesByProductId.getOrDefault(product.getId(), Collections.emptyList());

            // Generate Grounded Evidence
            PersonalizedEvidence evidence = personalizedEvidenceGenerator.generate(productDto, pScore, context);

            // Bounded Price Analytics (Optimized)
            com.pricepilot.intelligence.analytics.model.DealQuality dealQuality = null;
            com.pricepilot.intelligence.analytics.model.PriceTrend trend = null;
            com.pricepilot.intelligence.analytics.model.PurchaseSignal signal = null;
            Boolean isHistLow = null;

            try {
                ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(product.getId());
                if (analytics != null) {
                    dealQuality = analytics.getDealQuality();
                    trend = analytics.getTrend();
                    signal = analytics.getPurchaseSignal();
                    if (analytics.getHistoricalMin() != null && holder.bestPrice() != null
                            && holder.bestPrice().compareTo(analytics.getHistoricalMin()) <= 0) {
                        isHistLow = true;
                    }
                }
            } catch (Exception e) {
                log.warn("Price analytics unavailable for personalized discovery product {}: {}", product.getId(), e.getMessage());
            }

            // Map prices to DTO
            List<ProductPriceSearchResultDTO> priceDTOs = prodPrices.stream()
                    .map(ProductPriceSearchResultDTO::fromEntity)
                    .collect(Collectors.toList());

            List<String> badges = new ArrayList<>(scoredCand.getBadges());
            List<String> reasons = new ArrayList<>(scoredCand.getRelevanceReasons());

            if (evidence.hasPersonalization()) {
                badges.add(0, "Personalized Match");
                for (EvidenceItem item : evidence.getPositiveEvidence()) {
                    reasons.add(item.getDescription());
                }
            }

            if (isFirstPage && i == 0) {
                if (!badges.contains("Top Match") && !badges.contains("Best Match")) {
                    badges.add(0, "Top Match");
                }
            }

            DiscoveryProductDTO dto = DiscoveryProductDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .brand(product.getBrand())
                    .category(product.getCategory())
                    .description(product.getDescription())
                    .imageUrl(product.getImageUrl())
                    .archived(product.isArchived())
                    .createdAt(product.getCreatedAt())
                    .updatedAt(product.getUpdatedAt())
                    .currentBestPrice(holder.bestPrice())
                    .originalPrice(scoredCand.getOriginalPrice())
                    .discountPercentage(scoredCand.getDiscountPercentage())
                    .prices(priceDTOs)
                    .rating(extractRating(product))
                    .reviewCount(100L + (Math.abs(product.getId().hashCode()) % 900))
                    .inStock(!prodPrices.isEmpty())
                    .relevanceScore(pScore.getFinalScore())
                    .personalizedScore(pScore.getFinalScore())
                    .personalizationAdjustment(pScore.getPersonalizationAdjustment())
                    .personalizedEvidence(evidence)
                    .discoveryBadges(badges)
                    .discoveryReasons(reasons)
                    .dealQuality(dealQuality)
                    .priceTrend(trend)
                    .purchaseSignal(signal)
                    .isHistoricalLow(isHistLow != null ? isHistLow : false)
                    .build();

            discoveryProducts.add(dto);
        }

        // 12. Extract Available Facets
        Set<String> distinctCategories = new TreeSet<>();
        Set<String> distinctBrands = new TreeSet<>();
        for (ProductEntity p : candidateProducts) {
            if (p.getCategory() != null) distinctCategories.add(p.getCategory());
            if (p.getBrand() != null) distinctBrands.add(p.getBrand());
        }

        long duration = System.currentTimeMillis() - start;
        personalizedLatencyTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

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

    private double extractRating(ProductEntity product) {
        if (product == null || product.getId() == null) return 4.0;
        int hash = Math.abs(product.getId().hashCode());
        return 4.0 + ((hash % 11) / 10.0);
    }

    private void applyExplicitSort(List<PersonalizedCandidateHolder> candidates, String sortMode) {
        switch (sortMode) {
            case "price-asc", "price,asc", "price_low", "price" -> candidates.sort((c1, c2) -> {
                BigDecimal p1 = c1.bestPrice();
                BigDecimal p2 = c2.bestPrice();
                if (p1 != null && p2 != null) {
                    int cmp = p1.compareTo(p2);
                    if (cmp != 0) return cmp;
                } else if (p1 != null) return -1;
                else if (p2 != null) return 1;
                return c1.product().getId().compareTo(c2.product().getId());
            });
            case "price-desc", "price,desc", "price_high" -> candidates.sort((c1, c2) -> {
                BigDecimal p1 = c1.bestPrice();
                BigDecimal p2 = c2.bestPrice();
                if (p1 != null && p2 != null) {
                    int cmp = p2.compareTo(p1);
                    if (cmp != 0) return cmp;
                } else if (p1 != null) return -1;
                else if (p2 != null) return 1;
                return c1.product().getId().compareTo(c2.product().getId());
            });
            case "discount-desc", "discount,desc", "discount" -> candidates.sort((c1, c2) -> {
                BigDecimal d1 = c1.scoredCandidate().getDiscountPercentage();
                BigDecimal d2 = c2.scoredCandidate().getDiscountPercentage();
                int cmp = d2.compareTo(d1);
                if (cmp != 0) return cmp;
                return c1.product().getId().compareTo(c2.product().getId());
            });
            case "newest" -> candidates.sort((c1, c2) -> {
                var d1 = c1.product().getCreatedAt();
                var d2 = c2.product().getCreatedAt();
                if (d1 != null && d2 != null) {
                    int cmp = d2.compareTo(d1);
                    if (cmp != 0) return cmp;
                }
                return c1.product().getId().compareTo(c2.product().getId());
            });
            default -> {}
        }
    }

    private record PersonalizedCandidateHolder(
            ProductEntity product,
            ProductResponseDTO productDto,
            ScoredProductCandidate scoredCandidate,
            PersonalizedScore personalizedScore,
            BigDecimal bestPrice
    ) {}
}
