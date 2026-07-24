package com.pricepilot.intelligence.comparison;

import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Shopping Intelligence product matrix comparison.
 */
public interface ComparisonService {

    /**
     * Executes product matrix comparison for given product IDs and criteria.
     */
    ComparisonResponse compareProducts(ComparisonRequest request);

    /**
     * Retrieves a comparison matrix by product IDs directly.
     */
    ComparisonResponse compareProducts(List<UUID> productIds);

    /**
     * Retrieves an existing comparison session by session ID or token.
     */
    ComparisonResponse getComparisonSession(UUID sessionId);

    /**
     * Saves a comparison configuration for an authenticated user.
     */
    ComparisonResponse saveComparisonSession(UUID userId, ComparisonRequest request);
}
