package com.pricepilot.intelligence.alternative.controller;

import com.pricepilot.intelligence.alternative.model.AlternativeRequest;
import com.pricepilot.intelligence.alternative.model.AlternativeResponseDTO;
import com.pricepilot.intelligence.alternative.model.AlternativeType;
import com.pricepilot.intelligence.alternative.personalized.PersonalizedAlternativeService;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderService;
import com.pricepilot.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST Controller exposing Product Alternatives Discovery across product-driven
 * and query-driven entry points with support for public and personalized discovery.
 */
@RestController
@RequestMapping("/api/v1/alternatives")
@CrossOrigin(origins = "*")
public class AlternativeController {

    private final AlternativeFinderService alternativeFinderService;
    private final PersonalizedAlternativeService personalizedAlternativeService;

    public AlternativeController(
            AlternativeFinderService alternativeFinderService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PersonalizedAlternativeService personalizedAlternativeService) {
        this.alternativeFinderService = alternativeFinderService;
        this.personalizedAlternativeService = personalizedAlternativeService;
    }

    /**
     * Product-driven alternative discovery (public or personalized).
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
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean personalized,
            @AuthenticationPrincipal UserPrincipal principal) {

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
                .personalized(personalized)
                .build();

        if (personalized) {
            if (principal == null || principal.getId() == null) {
                throw new AccessDeniedException("Authentication required for personalized alternatives");
            }
            if (personalizedAlternativeService != null) {
                return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForProduct(productId, request, principal.getId()));
            }
        }

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForProduct(productId, request));
    }

    /**
     * Dedicated personalized product-driven alternative discovery.
     */
    @GetMapping("/product/{productId}/personalized")
    public ResponseEntity<AlternativeResponseDTO> getPersonalizedAlternativesForProduct(
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
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

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
                .personalized(true)
                .build();

        if (personalizedAlternativeService != null) {
            return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForProduct(productId, request, principal.getId()));
        }
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
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean personalized,
            @AuthenticationPrincipal UserPrincipal principal) {

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
                .personalized(personalized)
                .build();

        if (personalized) {
            if (principal == null || principal.getId() == null) {
                throw new AccessDeniedException("Authentication required for personalized alternatives");
            }
            if (personalizedAlternativeService != null) {
                return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForQuery(request, principal.getId()));
            }
        }

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForQuery(request));
    }

    /**
     * Query-driven alternative discovery via POST.
     */
    @PostMapping("/query")
    public ResponseEntity<AlternativeResponseDTO> postAlternativesForQuery(
            @RequestBody AlternativeRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean personalized,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isPersonalized = personalized || Boolean.TRUE.equals(request != null ? request.getPersonalized() : null);

        if (isPersonalized) {
            if (principal == null || principal.getId() == null) {
                throw new AccessDeniedException("Authentication required for personalized alternatives");
            }
            if (personalizedAlternativeService != null) {
                return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForQuery(request, principal.getId()));
            }
        }

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForQuery(request));
    }

    /**
     * Dedicated personalized query-driven alternative discovery via GET.
     */
    @GetMapping("/query/personalized")
    public ResponseEntity<AlternativeResponseDTO> getPersonalizedAlternativesForQuery(
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
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

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
                .personalized(true)
                .build();

        if (personalizedAlternativeService != null) {
            return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForQuery(request, principal.getId()));
        }
        return ResponseEntity.ok(alternativeFinderService.findAlternativesForQuery(request));
    }

    /**
     * Dedicated personalized query-driven alternative discovery via POST.
     */
    @PostMapping("/query/personalized")
    public ResponseEntity<AlternativeResponseDTO> postPersonalizedAlternativesForQuery(
            @RequestBody AlternativeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

        if (personalizedAlternativeService != null) {
            return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForQuery(request, principal.getId()));
        }
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
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean personalized,
            @AuthenticationPrincipal UserPrincipal principal) {

        String effectiveQuery = query != null ? query : q;

        if (personalized) {
            if (principal == null || principal.getId() == null) {
                throw new AccessDeniedException("Authentication required for personalized alternatives");
            }
            if (personalizedAlternativeService != null) {
                return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForNaturalLanguage(effectiveQuery, type, limit, principal.getId()));
            }
        }

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForNaturalLanguage(effectiveQuery, type, limit));
    }

    /**
     * Free-form Natural-Language alternative discovery via POST.
     */
    @PostMapping("/natural-language")
    public ResponseEntity<AlternativeResponseDTO> postNaturalLanguageAlternatives(
            @RequestBody AlternativeRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean personalized,
            @AuthenticationPrincipal UserPrincipal principal) {

        String query = request != null ? request.getQuery() : "";
        AlternativeType type = request != null ? request.getType() : AlternativeType.SIMILAR;
        int limit = request != null ? request.getLimit() : 10;
        boolean isPersonalized = personalized || Boolean.TRUE.equals(request != null ? request.getPersonalized() : null);

        if (isPersonalized) {
            if (principal == null || principal.getId() == null) {
                throw new AccessDeniedException("Authentication required for personalized alternatives");
            }
            if (personalizedAlternativeService != null) {
                return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForNaturalLanguage(query, type, limit, principal.getId()));
            }
        }

        return ResponseEntity.ok(alternativeFinderService.findAlternativesForNaturalLanguage(query, type, limit));
    }

    /**
     * Dedicated personalized Natural-Language alternative discovery via GET.
     */
    @GetMapping("/natural-language/personalized")
    public ResponseEntity<AlternativeResponseDTO> getPersonalizedNaturalLanguageAlternatives(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AlternativeType type,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Authentication required for personalized alternatives");
        }

        String effectiveQuery = query != null ? query : q;
        if (personalizedAlternativeService != null) {
            return ResponseEntity.ok(personalizedAlternativeService.findPersonalizedAlternativesForNaturalLanguage(effectiveQuery, type, limit, principal.getId()));
        }
        return ResponseEntity.ok(alternativeFinderService.findAlternativesForNaturalLanguage(effectiveQuery, type, limit));
    }
}
