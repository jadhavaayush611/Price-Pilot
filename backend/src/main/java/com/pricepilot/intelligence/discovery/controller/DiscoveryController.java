package com.pricepilot.intelligence.discovery.controller;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.dto.SearchSuggestionDTO;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Controller exposing intelligent product discovery and autocomplete suggestions.
 */
@RestController
@RequestMapping("/api/v1/discovery")
@CrossOrigin(origins = "*")
public class DiscoveryController {

    private final SearchDiscoveryService discoveryService;
    private final com.pricepilot.intelligence.discovery.hybrid.HybridSearchService hybridSearchService;
    private final com.pricepilot.intelligence.discovery.intent.NaturalLanguageDiscoveryService naturalLanguageDiscoveryService;

    public DiscoveryController(
            SearchDiscoveryService discoveryService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.pricepilot.intelligence.discovery.hybrid.HybridSearchService hybridSearchService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.pricepilot.intelligence.discovery.intent.NaturalLanguageDiscoveryService naturalLanguageDiscoveryService) {
        this.discoveryService = discoveryService;
        this.hybridSearchService = hybridSearchService;
        this.naturalLanguageDiscoveryService = naturalLanguageDiscoveryService;
    }

    @GetMapping("/products")
    public ResponseEntity<DiscoverySearchResponseDTO> discoverProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String dealQuality,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean hybrid,
            @RequestParam(defaultValue = "false") boolean naturalLanguage,
            @RequestParam(defaultValue = "false") boolean nl) {

        String effectiveQuery = query != null ? query : (q != null ? q : keyword);

        if ((naturalLanguage || nl) && naturalLanguageDiscoveryService != null) {
            com.pricepilot.intelligence.discovery.intent.NaturalLanguageSearchRequest nlRequest =
                    com.pricepilot.intelligence.discovery.intent.NaturalLanguageSearchRequest.builder()
                            .query(effectiveQuery)
                            .sort(sort)
                            .page(page)
                            .size(size)
                            .build();
            return ResponseEntity.ok(naturalLanguageDiscoveryService.search(nlRequest));
        }

        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query(effectiveQuery)
                .category(category)
                .brand(brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStock(inStock)
                .dealQuality(dealQuality)
                .sellerId(sellerId)
                .sort(sort)
                .page(page)
                .size(size)
                .build();

        if (hybrid && hybridSearchService != null) {
            return ResponseEntity.ok(hybridSearchService.search(request));
        }

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/hybrid")
    public ResponseEntity<DiscoverySearchResponseDTO> hybridSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String dealQuality,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String effectiveQuery = query != null ? query : (q != null ? q : keyword);

        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query(effectiveQuery)
                .category(category)
                .brand(brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStock(inStock)
                .dealQuality(dealQuality)
                .sellerId(sellerId)
                .sort(sort)
                .page(page)
                .size(size)
                .build();

        if (hybridSearchService != null) {
            return ResponseEntity.ok(hybridSearchService.search(request));
        }
        return ResponseEntity.ok(discoveryService.searchAndDiscover(request));
    }

    @GetMapping("/natural-language")
    public ResponseEntity<DiscoverySearchResponseDTO> naturalLanguageSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "0.70") double structuredWeight,
            @RequestParam(defaultValue = "0.30") double semanticWeight) {

        String effectiveQuery = query != null ? query : q;

        if (naturalLanguageDiscoveryService != null) {
            com.pricepilot.intelligence.discovery.intent.NaturalLanguageSearchRequest nlRequest =
                    com.pricepilot.intelligence.discovery.intent.NaturalLanguageSearchRequest.builder()
                            .query(effectiveQuery)
                            .sort(sort)
                            .page(page)
                            .size(size)
                            .structuredWeight(structuredWeight)
                            .semanticWeight(semanticWeight)
                            .build();
            return ResponseEntity.ok(naturalLanguageDiscoveryService.search(nlRequest));
        }

        // Fallback to standard discovery if NL service is not configured
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query(effectiveQuery)
                .sort(sort)
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(discoveryService.searchAndDiscover(request));
    }

    @GetMapping("/intent")
    public ResponseEntity<com.pricepilot.intelligence.discovery.intent.ShoppingQueryIntent> getQueryIntent(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q) {

        String effectiveQuery = query != null ? query : q;
        if (naturalLanguageDiscoveryService != null && effectiveQuery != null) {
            return ResponseEntity.ok(naturalLanguageDiscoveryService.interpretQuery(effectiveQuery));
        }
        return ResponseEntity.ok(
                com.pricepilot.intelligence.discovery.intent.ShoppingQueryIntent.builder()
                        .rawQuery(effectiveQuery != null ? effectiveQuery : "")
                        .semanticQuery(effectiveQuery != null ? effectiveQuery : "")
                        .build()
        );
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SearchSuggestionDTO>> getSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "6") int limit) {
        List<SearchSuggestionDTO> suggestions = discoveryService.getSuggestions(query, limit);
        return ResponseEntity.ok(suggestions);
    }
}
