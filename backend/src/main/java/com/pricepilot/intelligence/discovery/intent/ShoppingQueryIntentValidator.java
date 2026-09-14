package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;

/**
 * Validator contract for validating ShoppingQueryIntent before passing into the HybridSearch engine.
 */
public interface ShoppingQueryIntentValidator {

    /**
     * Validates and normalizes shopping query intent constraints.
     *
     * @param intent The parsed ShoppingQueryIntent
     * @return ShoppingQueryValidationResult with validation status and normalized intent
     */
    ShoppingQueryValidationResult validate(ShoppingQueryIntent intent);

    /**
     * Transforms a validated ShoppingQueryIntent into an executable HybridSearchRequest.
     *
     * @param intent Validated shopping query intent
     * @param page Target page index (0-based)
     * @param size Page size
     * @param sortOverride Optional sort mode override from client
     * @return Fully configured HybridSearchRequest
     */
    HybridSearchRequest toHybridSearchRequest(ShoppingQueryIntent intent, int page, int size, String sortOverride);
}
