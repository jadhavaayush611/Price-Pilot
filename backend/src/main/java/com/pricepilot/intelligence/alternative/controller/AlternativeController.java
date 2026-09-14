package com.pricepilot.intelligence.alternative.controller;

import com.pricepilot.intelligence.alternative.model.AlternativeRequest;
import com.pricepilot.intelligence.alternative.model.AlternativeResponseDTO;
import com.pricepilot.intelligence.alternative.model.AlternativeType;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST Controller exposing Product Alternatives Discovery across product-driven
 * and query-driven entry points.
 */
@RestController
@RequestMapping("/api/v1/alternatives")
@CrossOrigin(origins = "*")
public class AlternativeController {

    private final AlternativeFinderService alternativeFinderService;

    public AlternativeController(AlternativeFinderService alternativeFinderService) {
        this.alternativeFinderService = alternativeFinderService;
    }

    /**
     * Product-driven alternative discovery.
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<AlternativeResponseDTO> getAlternativesForProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false, defaultValue = "SIMILAR") AlternativeType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Double minSemanticSimilarity,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        AlternativeRequest request = AlternativeRequest.builder()
                .productId(productId)
                .type(type)
                .category(category)
                .brand(brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStock(inStock)
                .minSemanticSimilarity(minSemanticSimilarity)
                .sellerId(sellerId)
                .limit(limit)
                .build();

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForProduct(productId, request));
    }

    /**
     * Query-driven alternative discovery via GET.
     */
    @GetMapping("/query")
    public ResponseEntity<AlternativeResponseDTO> getAlternativesForQuery(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "SIMILAR") AlternativeType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        String effectiveQuery = query != null ? query : q;
        AlternativeRequest request = AlternativeRequest.builder()
                .query(effectiveQuery)
                .type(type)
                .category(category)
                .brand(brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStock(inStock)
                .limit(limit)
                .build();

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForQuery(request));
    }

    /**
     * Query-driven alternative discovery via POST.
     */
    @PostMapping("/query")
    public ResponseEntity<AlternativeResponseDTO> postAlternativesForQuery(@RequestBody AlternativeRequest request) {
        return ResponseEntity.ok(alternativeFinderService.findAlternativesForQuery(request));
    }

    /**
     * Free-form Natural-Language alternative discovery via GET.
     */
    @GetMapping("/natural-language")
    public ResponseEntity<AlternativeResponseDTO> getNaturalLanguageAlternatives(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AlternativeType type,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        String effectiveQuery = query != null ? query : q;
        return ResponseEntity.ok(alternativeFinderService.findAlternativesForNaturalLanguage(effectiveQuery, type, limit));
    }

    /**
     * Free-form Natural-Language alternative discovery via POST.
     */
    @PostMapping("/natural-language")
    public ResponseEntity<AlternativeResponseDTO> postNaturalLanguageAlternatives(@RequestBody AlternativeRequest request) {
        String query = request != null ? request.getQuery() : "";
        AlternativeType type = request != null ? request.getType() : AlternativeType.SIMILAR;
        int limit = request != null ? request.getLimit() : 10;
        return ResponseEntity.ok(alternativeFinderService.findAlternativesForNaturalLanguage(query, type, limit));
    }
}
