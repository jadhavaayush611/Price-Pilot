package com.pricepilot.intelligence.comparison;

import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Shopping Intelligence product matrix comparison.
 */
public interface ComparisonService {

    /**
     * Executes product matrix comparison for given request criteria.
     */
    ComparisonResponse compareProducts(ComparisonRequest request);

    /**
     * Retrieves a comparison matrix by product IDs directly (supporting 2-5 products).
     */
    ComparisonResponse compareProducts(List<UUID> productIds);

    /**
     * Retrieves an existing comparison session or saved comparison with ownership validation.
     */
    ComparisonResponse getComparisonSession(UUID sessionId, UUID authenticatedUserId);

    /**
     * Saves a comparison configuration for an authenticated user.
     */
    SavedComparisonResponseDTO saveComparisonSession(UUID userId, ComparisonRequest request);

    /**
     * Retrieves paginated saved comparisons for an authenticated user.
     */
    Page<SavedComparisonResponseDTO> getSavedComparisons(UUID userId, int page, int size);

    /**
     * Retrieves paginated, sorted, and filtered saved comparisons for an authenticated user.
     */
    Page<SavedComparisonResponseDTO> getSavedComparisons(UUID userId, int page, int size, String sortKey, String sortDir, String search);

    /**
     * Deletes a saved comparison or session with ownership validation.
     */
    void deleteSavedComparison(UUID userId, UUID comparisonId);
}
