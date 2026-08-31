package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.Comparator;

public interface PersonalizationScorer {
    PersonalizationResult scorePersonalization(
            ProductResponseDTO product,
            ProductScore baseScore,
            UserShoppingPreferenceEntity preferences,
            UserShoppingSignals signals
    );

    Comparator<ProductResponseDTO> getDeterministicPersonalizedComparator(
            java.util.Map<java.util.UUID, PersonalizationResult> results
    );
}
