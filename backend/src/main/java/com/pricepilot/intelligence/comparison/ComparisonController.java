package com.pricepilot.intelligence.comparison;

import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<ComparisonResponse> getComparison(
            @RequestParam(required = false) String ids,
            @RequestParam(required = false) UUID sessionId) {
        
        if (sessionId != null) {
            return ResponseEntity.ok(comparisonService.getComparisonSession(sessionId));
        }

        if (ids != null && !ids.isBlank()) {
            List<UUID> productIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(comparisonService.compareProducts(productIds));
        }

        return ResponseEntity.ok(comparisonService.compareProducts(List.of()));
    }

    @PostMapping
    public ResponseEntity<ComparisonResponse> createComparison(@RequestBody ComparisonRequest request) {
        ComparisonResponse response = comparisonService.compareProducts(request);
        return ResponseEntity.ok(response);
    }
}
