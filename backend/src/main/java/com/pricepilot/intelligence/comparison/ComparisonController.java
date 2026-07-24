package com.pricepilot.intelligence.comparison;

import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import com.pricepilot.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for Shopping Intelligence product comparisons.
 * Standardized API endpoint base path: /api/v1/compare
 */
@RestController
@RequestMapping("/api/v1/compare")
@CrossOrigin(origins = "*")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * Public endpoint to get matrix comparison by product IDs or session ID query parameters.
     */
    @GetMapping
    public ResponseEntity<ComparisonResponse> getComparison(
            @RequestParam(required = false) String ids,
            @RequestParam(required = false) UUID sessionId) {

        UUID authenticatedUserId = getAuthenticatedUserId();

        if (sessionId != null) {
            return ResponseEntity.ok(comparisonService.getComparisonSession(sessionId, authenticatedUserId));
        }

        if (ids != null && !ids.isBlank()) {
            List<UUID> productIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(comparisonService.compareProducts(productIds));
        }

        return ResponseEntity.ok(comparisonService.compareProducts(List.of()));
    }

    /**
     * Public endpoint to generate product comparison matrix via request body.
     */
    @PostMapping
    public ResponseEntity<ComparisonResponse> createComparison(@RequestBody ComparisonRequest request) {
        ComparisonResponse response = comparisonService.compareProducts(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticated endpoint to save a comparison matrix session.
     * Path: POST /api/v1/compare/save
     */
    @PostMapping("/save")
    public ResponseEntity<SavedComparisonResponseDTO> saveComparison(@RequestBody ComparisonRequest request) {
        UUID userId = getAuthenticatedUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to save comparisons");
        }
        SavedComparisonResponseDTO response = comparisonService.saveComparisonSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticated endpoint to list saved comparisons with pagination, sorting, and filtering.
     * Path: GET /api/v1/compare/saved
     */
    @GetMapping("/saved")
    public ResponseEntity<Page<SavedComparisonResponseDTO>> getSavedComparisons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortKey,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search) {
        UUID userId = getAuthenticatedUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to view saved comparisons");
        }
        return ResponseEntity.ok(comparisonService.getSavedComparisons(userId, page, size, sortKey, sortDir, search));
    }

    /**
     * Endpoint to retrieve comparison session or saved comparison by ID.
     * Path: GET /api/v1/compare/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ComparisonResponse> getComparisonBySessionId(@PathVariable UUID sessionId) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        ComparisonResponse response = comparisonService.getComparisonSession(sessionId, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticated endpoint to delete a saved comparison or session by ID.
     * Path: DELETE /api/v1/compare/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteComparison(@PathVariable UUID sessionId) {
        UUID userId = getAuthenticatedUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to delete comparisons");
        }
        comparisonService.deleteSavedComparison(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) auth.getPrincipal()).getId();
        }
        return null;
    }
}
