package com.pricepilot.intelligence.personalization.signals;

import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.product.ProductEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BehavioralSignalServiceImpl implements BehavioralSignalService {

    private static final int MAX_EVENTS_WINDOW = 50;
    private static final int DEDUPLICATION_WINDOW_SECONDS = 180; // 3 minutes deduplication window
    private static final int MAX_PRODUCT_REPETITIONS = 5; // Satiation cap per product

    private final UserInteractionEventRepository eventRepository;

    public BehavioralSignalServiceImpl(UserInteractionEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserShoppingSignals extractSignals(UUID userId) {
        if (userId == null) {
            return UserShoppingSignals.builder().userId(null).build();
        }

        List<UserInteractionEventEntity> recentEvents = eventRepository.findByUserIdWithRelations(
                userId, PageRequest.of(0, MAX_EVENTS_WINDOW)
        );

        return aggregateSignalsFromEvents(userId, recentEvents);
    }

    public UserShoppingSignals aggregateSignalsFromEvents(UUID userId, List<UserInteractionEventEntity> recentEvents) {
        if (recentEvents == null || recentEvents.isEmpty()) {
            return UserShoppingSignals.builder().userId(userId).build();
        }

        Map<String, Double> categoryScores = new HashMap<>();
        Map<String, Double> brandScores = new HashMap<>();
        Set<UUID> interactedProducts = new HashSet<>();
        Map<InteractionType, Integer> countsByType = new EnumMap<>(InteractionType.class);

        // Deduplication & Replay Protection tracker
        Map<String, LocalDateTime> lastEventTimes = new HashMap<>();
        Map<UUID, Integer> productRepetitions = new HashMap<>();

        for (UserInteractionEventEntity event : recentEvents) {
            InteractionType type = event.getInteractionType();
            if (type == null) continue;

            ProductEntity product = event.getProduct();
            UUID prodId = product != null ? product.getId() : null;

            // Duplicate prevention check
            String dedupKey = type.name() + ":" + (prodId != null ? prodId.toString() : "global");
            LocalDateTime eventTime = event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now();

            if (lastEventTimes.containsKey(dedupKey)) {
                LocalDateTime prevTime = lastEventTimes.get(dedupKey);
                long secondsDiff = Math.abs(Duration.between(prevTime, eventTime).getSeconds());
                if (secondsDiff < DEDUPLICATION_WINDOW_SECONDS) {
                    // Suppress duplicate spam within window
                    continue;
                }
            }
            lastEventTimes.put(dedupKey, eventTime);

            // Satiation / repetitive view cap
            if (prodId != null) {
                int count = productRepetitions.getOrDefault(prodId, 0);
                if (count >= MAX_PRODUCT_REPETITIONS) {
                    continue;
                }
                productRepetitions.put(prodId, count + 1);
                interactedProducts.add(prodId);
            }

            countsByType.merge(type, 1, Integer::sum);
            double weight = getInteractionWeight(type);

            if (product != null) {
                if (product.getCategory() != null && !product.getCategory().trim().isEmpty()) {
                    String cat = product.getCategory().toLowerCase().trim();
                    categoryScores.merge(cat, weight, Double::sum);
                }
                if (product.getBrand() != null && !product.getBrand().trim().isEmpty()) {
                    String br = product.getBrand().toLowerCase().trim();
                    brandScores.merge(br, weight, Double::sum);
                }
            } else if (event.getMetadata() != null && type == InteractionType.SEARCH) {
                // Search category metadata if present
                Object catMeta = event.getMetadata().get("category");
                if (catMeta instanceof String s && !s.trim().isEmpty() && !s.equalsIgnoreCase("All")) {
                    categoryScores.merge(s.toLowerCase().trim(), weight, Double::sum);
                }
            }
        }

        // Normalize affinity scores to 0.0 .. 1.0 range
        Map<String, Double> normalizedCategories = normalizeAffinities(categoryScores);
        Map<String, Double> normalizedBrands = normalizeAffinities(brandScores);

        int totalInteractions = countsByType.values().stream().mapToInt(Integer::intValue).sum();

        return UserShoppingSignals.builder()
                .userId(userId)
                .categoryAffinity(normalizedCategories)
                .brandAffinity(normalizedBrands)
                .recentInteractedProductIds(interactedProducts)
                .interactionCountsByType(countsByType)
                .totalInteractions(totalInteractions)
                .build();
    }

    private double getInteractionWeight(InteractionType type) {
        return switch (type) {
            case PRODUCT_SAVE, WATCHLIST_CREATE -> 3.0;
            case COMPARISON_VIEW, ALERT_INTERACTION, RECOMMENDATION_INTERACTION -> 2.0;
            case PRODUCT_VIEW, PRICE_HISTORY_VIEW -> 1.0;
            case SEARCH -> 1.0;
            default -> 0.5;
        };
    }

    private Map<String, Double> normalizeAffinities(Map<String, Double> rawScores) {
        if (rawScores.isEmpty()) {
            return Collections.emptyMap();
        }
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max <= 0.0) {
            return Collections.emptyMap();
        }

        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            double norm = Math.round((entry.getValue() / max) * 100.0) / 100.0;
            normalized.put(entry.getKey(), Math.min(1.0, Math.max(0.0, norm)));
        }
        return normalized;
    }
}
