package com.pricepilot.intelligence.marketplace.adapter;

import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.normalizer.OfferNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Minimal orchestration service to query configured marketplace adapters,
 * normalize their raw outputs through OfferNormalizer, deduplicate offers deterministically,
 * and handle provider failures safely without losing catalog results.
 */
@Service
public class MarketplaceOfferAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceOfferAggregatorService.class);

    private final List<MarketplaceOfferAdapter> adapters;
    private final OfferNormalizer offerNormalizer;

    public MarketplaceOfferAggregatorService(List<MarketplaceOfferAdapter> adapters, OfferNormalizer offerNormalizer) {
        this.adapters = adapters != null ? adapters : Collections.emptyList();
        this.offerNormalizer = offerNormalizer;
    }

    /**
     * Fetches offers across all available adapters for the given query context,
     * normalizes them, filters duplicates deterministically, and ranks them by lowest base price.
     */
    public List<NormalizedOfferDTO> aggregateOffers(MarketplaceQueryContext context) {
        if (context == null) {
            return Collections.emptyList();
        }

        List<NormalizedOfferDTO> allOffers = new ArrayList<>();
        // Deduplication set based on reliable identity: providerId:sellerName:offerUrl
        Set<String> seenDeduplicationKeys = new HashSet<>();

        for (MarketplaceOfferAdapter adapter : adapters) {
            if (adapter == null || !adapter.isAvailable()) {
                continue;
            }

            try {
                List<RawMarketplaceOffer> rawOffers = adapter.fetchRawOffers(context);
                if (rawOffers == null || rawOffers.isEmpty()) {
                    continue;
                }

                ProvenanceType sourceType = "catalog".equalsIgnoreCase(adapter.getProviderId())
                        ? ProvenanceType.DEMO
                        : ProvenanceType.DEMO; // Mocks are always DEMO / unverified

                for (RawMarketplaceOffer raw : rawOffers) {
                    NormalizedOfferDTO normalized = offerNormalizer.normalize(raw, context, sourceType, adapter.getProviderId());
                    if (normalized == null) {
                        // Price safety gate or validation rejected this offer
                        continue;
                    }

                    String dedupKey = buildDeduplicationKey(adapter.getProviderId(), normalized);
                    if (seenDeduplicationKeys.add(dedupKey)) {
                        allOffers.add(normalized);
                    } else {
                        log.debug("Discarding duplicate offer with key {}", dedupKey);
                    }
                }
            } catch (Exception ex) {
                // Adapter failure must NEVER cause loss of results from other adapters
                log.warn("PARTIAL_AGGREGATION_FAILURE: Adapter '{}' failed during offer aggregation for product ID '{}' ({}): {}",
                        adapter.getProviderId(), context.productId(), context.productName(), ex.getMessage(), ex);
            }
        }

        return offerNormalizer.rankAndEvaluateOffers(allOffers);
    }

    /**
     * Deterministic deduplication key combining provider ID, seller name, and offer URL / product ID.
     * Offers are never merged solely because their prices match.
     */
    private String buildDeduplicationKey(String providerId, NormalizedOfferDTO offer) {
        String seller = (offer.getSource() != null && offer.getSource().getSellerName() != null)
                ? offer.getSource().getSellerName().trim().toLowerCase()
                : "unknown";
        String url = (offer.getSource() != null && offer.getSource().getOfferUrl() != null)
                ? offer.getSource().getOfferUrl().trim().toLowerCase()
                : "";
        String prodId = offer.getProductId() != null ? offer.getProductId().toString() : "";

        return providerId.toLowerCase() + "::" + seller + "::" + prodId + "::" + url;
    }
}
