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

    public DiscoveryController(SearchDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
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

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SearchSuggestionDTO>> getSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "6") int limit) {
        List<SearchSuggestionDTO> suggestions = discoveryService.getSuggestions(query, limit);
        return ResponseEntity.ok(suggestions);
    }
}
