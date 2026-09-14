package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrator implementing Natural-Language Shopping Queries (PricePilot v1.2 Phase 4).
 *
 * Core Principle:
 * "Natural language is an input mechanism, not a source of truth.
 * Natural-language understanding interprets user intent; deterministic PricePilot intelligence
 * determines product eligibility, hard constraints, and final ranking."
 */
@Service
public class NaturalLanguageDiscoveryServiceImpl implements NaturalLanguageDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageDiscoveryServiceImpl.class);

    private final ShoppingQueryInterpreter shoppingQueryInterpreter;
    private final ShoppingQueryIntentValidator intentValidator;
    private final HybridSearchService hybridSearchService;
    private final ProductRepository productRepository;

    // Observability Metrics
    private final Counter requestCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter conflictCounter;
    private final Counter degradedCounter;
    private final Timer searchTimer;
    private final Timer interpretationTimer;

    public NaturalLanguageDiscoveryServiceImpl(
            ShoppingQueryInterpreter shoppingQueryInterpreter,
            ShoppingQueryIntentValidator intentValidator,
            HybridSearchService hybridSearchService,
            ProductRepository productRepository,
            MeterRegistry meterRegistry) {
        this.shoppingQueryInterpreter = shoppingQueryInterpreter;
        this.intentValidator = intentValidator;
        this.hybridSearchService = hybridSearchService;
        this.productRepository = productRepository;

        this.requestCounter = Counter.builder("pricepilot.nl.search.requests")
                .description("Total natural-language search requests initiated")
                .register(meterRegistry);

        this.successCounter = Counter.builder("pricepilot.nl.search.success")
                .description("Total successful natural-language searches")
                .register(meterRegistry);

        this.failureCounter = Counter.builder("pricepilot.nl.search.failures")
                .description("Total failed natural-language searches")
                .register(meterRegistry);

        this.conflictCounter = Counter.builder("pricepilot.nl.search.conflicts")
                .description("Total natural-language queries with contradictory/conflicting constraints")
                .register(meterRegistry);

        this.degradedCounter = Counter.builder("pricepilot.nl.search.degraded")
                .description("Total times natural-language search degraded to raw semantic retrieval")
                .register(meterRegistry);

        this.searchTimer = Timer.builder("pricepilot.nl.search.duration")
                .description("Total latency of natural-language search pipeline")
                .register(meterRegistry);

        this.interpretationTimer = Timer.builder("pricepilot.nl.interpretation.duration")
                .description("Latency of natural-language query intent interpretation")
                .register(meterRegistry);
    }

    @Override
    public ShoppingQueryIntent interpretQuery(String rawQuery) {
        long start = System.currentTimeMillis();
        try {
            return shoppingQueryInterpreter.interpret(rawQuery);
        } finally {
            interpretationTimer.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public DiscoverySearchResponseDTO search(NaturalLanguageSearchRequest request) {
        requestCounter.increment();
        long start = System.currentTimeMillis();

        String rawQuery = request != null && request.getQuery() != null ? request.getQuery().trim() : "";
        int page = request != null ? Math.max(0, request.getPage()) : 0;
        int size = request != null && request.getSize() > 0 ? request.getSize() : 10;
        String sort = request != null ? request.getSort() : "relevance";

        if (rawQuery.isEmpty()) {
            return buildEmptyResponse(page, size, sort, Collections.emptyList());
        }

        try {
            // 1. Interpret Natural Language Query
            long interpStart = System.currentTimeMillis();
            ShoppingQueryIntent intent;
            try {
                intent = shoppingQueryInterpreter.interpret(rawQuery);
            } catch (Exception e) {
                degradedCounter.increment();
                log.warn("NL interpreter failed for query '{}', falling back to raw semantic search: {}", rawQuery, e.getMessage());
                intent = ShoppingQueryIntent.builder()
                        .rawQuery(rawQuery)
                        .semanticQuery(rawQuery)
                        .confidenceNotes(List.of("Interpreter fallback: raw query used as semantic text"))
                        .build();
            }
            interpretationTimer.record(System.currentTimeMillis() - interpStart, TimeUnit.MILLISECONDS);

            // 2. Validate Intent
            ShoppingQueryValidationResult validation = intentValidator.validate(intent);

            // 3. Handle Contradictory Conflicts
            if (validation.isHasConflicts()) {
                conflictCounter.increment();
                log.info("Conflicting constraints in NL query '{}': {}", rawQuery, validation.getErrors());
                return buildConflictResponse(intent, validation.getErrors(), page, size, sort);
            }

            // 4. Map Intent to HybridSearchRequest
            HybridSearchRequest hybridRequest = intentValidator.toHybridSearchRequest(intent, page, size, sort);
            if (request != null && request.getStructuredWeight() > 0) {
                hybridRequest.setStructuredWeight(request.getStructuredWeight());
            }
            if (request != null && request.getSemanticWeight() > 0) {
                hybridRequest.setSemanticWeight(request.getSemanticWeight());
            }

            // 5. Execute Hybrid Search
            DiscoverySearchResponseDTO response = hybridSearchService.search(hybridRequest);

            // 6. Enrich InterpretedQuery with Natural Language Intent Metadata
            InterpretedQuery enrichedInterpretedQuery = InterpretedQuery.builder()
                    .originalQuery(rawQuery)
                    .normalizedQuery(intent.getRawQuery())
                    .cleanSearchTerms(intent.getSemanticQuery())
                    .detectedCategory(intent.getCategory())
                    .detectedBrand(intent.getBrand())
                    .minPrice(intent.getMinPrice())
                    .maxPrice(intent.getMaxPrice())
                    .minRating(intent.getMinRating())
                    .inStockOnly(intent.getInStock())
                    .dealIntent(intent.getDealIntent())
                    .interpretationNotes(intent.getConfidenceNotes())
                    .build();

            successCounter.increment();
            searchTimer.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);

            response.setInterpretedQuery(enrichedInterpretedQuery);
            return response;

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Natural language search failed for query '{}'", rawQuery, e);
            throw e;
        }
    }

    private DiscoverySearchResponseDTO buildConflictResponse(
            ShoppingQueryIntent intent,
            List<String> errors,
            int page,
            int size,
            String sort) {

        List<String> notes = new ArrayList<>(intent.getConfidenceNotes());
        notes.addAll(errors);

        InterpretedQuery interpreted = InterpretedQuery.builder()
                .originalQuery(intent.getRawQuery())
                .cleanSearchTerms(intent.getSemanticQuery())
                .detectedCategory(intent.getCategory())
                .detectedBrand(intent.getBrand())
                .interpretationNotes(notes)
                .build();

        List<String> categories = Collections.emptyList();
        List<String> brands = Collections.emptyList();
        try {
            List<String> repoCats = productRepository.findDistinctCategories();
            if (repoCats != null) categories = repoCats;
            List<String> repoBrands = productRepository.findDistinctBrands();
            if (repoBrands != null) brands = repoBrands;
        } catch (Exception ignored) {}

        return DiscoverySearchResponseDTO.builder()
                .content(Collections.emptyList())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .interpretedQuery(interpreted)
                .appliedSort(sort)
                .executionTimeMs(0)
                .availableCategories(categories)
                .availableBrands(brands)
                .build();
    }

    private DiscoverySearchResponseDTO buildEmptyResponse(int page, int size, String sort, List<String> notes) {
        List<String> categories = Collections.emptyList();
        List<String> brands = Collections.emptyList();
        try {
            List<String> repoCats = productRepository.findDistinctCategories();
            if (repoCats != null) categories = repoCats;
            List<String> repoBrands = productRepository.findDistinctBrands();
            if (repoBrands != null) brands = repoBrands;
        } catch (Exception ignored) {}

        return DiscoverySearchResponseDTO.builder()
                .content(Collections.emptyList())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .interpretedQuery(InterpretedQuery.builder().interpretationNotes(notes).build())
                .appliedSort(sort)
                .executionTimeMs(0)
                .availableCategories(categories)
                .availableBrands(brands)
                .build();
    }
}
