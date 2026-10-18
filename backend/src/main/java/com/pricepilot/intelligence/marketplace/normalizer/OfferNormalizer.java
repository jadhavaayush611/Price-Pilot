package com.pricepilot.intelligence.marketplace.normalizer;

import com.pricepilot.intelligence.marketplace.adapter.MarketplaceQueryContext;
import com.pricepilot.intelligence.marketplace.adapter.RawMarketplaceOffer;
import com.pricepilot.intelligence.marketplace.dto.*;
import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Normalizes raw and DTO product price representations into NormalizedOfferDTOs.
 * Strictly adheres to the Marketplace Data Integrity Contract:
 * - Missing shipping or checkout fees are NEVER inferred as zero.
 * - Catalog seed and demo data is labeled unverified; never claimed as live verified.
 * - Stock states are never fabricated.
 * - Missing observation timestamps are treated as unknown freshness, never silently assumed fresh.
 * - Future timestamps beyond 5 minutes are rejected as untrustworthy observations.
 */
@Component
public class OfferNormalizer {

    public static final int DEFAULT_STALE_THRESHOLD_HOURS = 168; // 7 days
    private static final long MAX_ALLOWED_FUTURE_SKEW_SECONDS = 300; // 5 minutes

    private final Clock clock;

    public OfferNormalizer() {
        this(Clock.systemDefaultZone());
    }

    public OfferNormalizer(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    /**
     * Internal freshness assessment record.
     */
    private record FreshnessResult(
            LocalDateTime effectiveObservedAt,
            boolean isStale,
            boolean isUnknownFreshness
    ) {}

    /**
     * Truthfully assesses observation freshness:
     * - Null timestamp -> unknown freshness (cannot be confirmed fresh).
     * - Future timestamp > 5 mins -> rejected as untrustworthy (null observedAt, unknown freshness).
     * - Exact duration > 168 hours -> stale. At exactly 168 hours, it is not stale.
     */
    private FreshnessResult assessFreshness(LocalDateTime observedAt) {
        if (observedAt == null) {
            return new FreshnessResult(null, false, true);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Duration durationFromObserved = Duration.between(observedAt, now);

        // If observedAt is in the future by more than 5 minutes (300 seconds)
        if (durationFromObserved.getSeconds() < -MAX_ALLOWED_FUTURE_SKEW_SECONDS) {
            // Untrustworthy future observation timestamp
            return new FreshnessResult(null, false, true);
        }

        long thresholdSeconds = (long) DEFAULT_STALE_THRESHOLD_HOURS * 3600;
        // Strictly greater than 168 hours (604,800 seconds). At exactly 168h, it is not stale.
        boolean isStale = durationFromObserved.getSeconds() > thresholdSeconds;
        return new FreshnessResult(observedAt, isStale, false);
    }

    /**
     * Normalizes a ProductPriceResponseDTO into a NormalizedOfferDTO.
     */
    public NormalizedOfferDTO normalize(ProductPriceResponseDTO dto) {
        if (dto == null) {
            return null;
        }

        // Price Safety Gate: Never convert an invalid, missing, or negative price into a valid zero-price offer.
        // Legitimate zero prices (compareTo(BigDecimal.ZERO) == 0) are accepted only when explicitly provided.
        if (dto.getCurrentPrice() == null || dto.getCurrentPrice().compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        UUID sellerId = dto.getSeller() != null ? dto.getSeller().getId() : null;
        String sellerName = dto.getSeller() != null && dto.getSeller().getName() != null
                ? dto.getSeller().getName().trim()
                : "Market Merchant";
        String websiteUrl = dto.getSeller() != null ? dto.getSeller().getWebsiteUrl() : null;
        String logoUrl = dto.getSeller() != null ? dto.getSeller().getLogoUrl() : null;
        String offerUrl = dto.getProductUrl() != null ? dto.getProductUrl() : (websiteUrl != null ? websiteUrl : "#");

        OfferSourceDTO source = OfferSourceDTO.builder()
                .sellerId(sellerId)
                .sellerName(sellerName)
                .websiteUrl(websiteUrl)
                .logoUrl(logoUrl)
                .offerUrl(offerUrl)
                .build();

        BigDecimal basePrice = dto.getCurrentPrice();
        BigDecimal originalPrice = dto.getOriginalPrice() != null && dto.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0
                ? dto.getOriginalPrice()
                : null;

        BigDecimal discountPct = BigDecimal.ZERO;
        if (dto.getDiscountPercentage() != null && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            discountPct = dto.getDiscountPercentage();
        } else if (originalPrice != null && originalPrice.compareTo(basePrice) > 0) {
            discountPct = originalPrice.subtract(basePrice)
                    .divide(originalPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        OfferPricingDTO pricing = OfferPricingDTO.builder()
                .basePrice(basePrice)
                .originalPrice(originalPrice)
                .discountPercentage(discountPct)
                .currency("USD")
                .shippingPrice(null) // Never infer missing shipping to be zero
                .taxesAndFees(null)
                .totalPrice(null)
                .basePriceOnly(true)
                .pricingDisclaimer("Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.")
                .build();

        FreshnessResult freshness = assessFreshness(dto.getLastUpdated());
        boolean isStale = freshness.isStale();
        LocalDateTime effectiveObservedAt = freshness.effectiveObservedAt();

        String trustLabel;
        String trustDesc;
        if (isStale) {
            trustLabel = "Catalog Offer (Stale)";
            trustDesc = "Catalog seed offer has exceeded the freshness window. Not a live verified retailer feed.";
        } else if (freshness.isUnknownFreshness()) {
            trustLabel = "Catalog Offer (Unverified Freshness)";
            trustDesc = "Catalog seed offer with unverified observation timestamp. Not a live verified retailer feed.";
        } else {
            trustLabel = "Catalog Offer";
            trustDesc = "Demonstration catalog seed offer. Not a live verified retailer feed.";
        }

        OfferProvenanceDTO provenance = OfferProvenanceDTO.builder()
                .sourceType(ProvenanceType.DEMO)
                .verificationStatus(VerificationStatus.UNVERIFIED) // Stale status does not imply a verified source
                .liveVerified(false) // Demo/seed data is never live verified
                .observedAt(effectiveObservedAt)
                .rawLastUpdated(effectiveObservedAt != null ? effectiveObservedAt.toString() : null)
                .stale(isStale)
                .trustLabel(trustLabel)
                .trustDescription(trustDesc)
                .build();

        OfferAvailabilityDTO availability = OfferAvailabilityDTO.builder()
                .status(AvailabilityStatus.UNKNOWN)
                .available(null)
                .stockQuantity(null)
                .build();

        UUID productId = dto.getProduct() != null ? dto.getProduct().getId() : null;
        String productTitle = dto.getProduct() != null ? dto.getProduct().getName() : null;

        return NormalizedOfferDTO.builder()
                .id(dto.getId())
                .productId(productId)
                .productTitle(productTitle)
                .source(source)
                .pricing(pricing)
                .provenance(provenance)
                .availability(availability)
                .build();
    }

    /**
     * Normalizes a ProductPriceEntity into a NormalizedOfferDTO.
     */
    public NormalizedOfferDTO normalize(ProductPriceEntity entity) {
        if (entity == null) {
            return null;
        }

        // Price Safety Gate: Never convert an invalid, missing, or negative price into a valid zero-price offer.
        // Legitimate zero prices (compareTo(BigDecimal.ZERO) == 0) are accepted only when explicitly provided.
        if (entity.getCurrentPrice() == null || entity.getCurrentPrice().compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        UUID sellerId = entity.getSeller() != null ? entity.getSeller().getId() : null;
        String sellerName = entity.getSeller() != null && entity.getSeller().getName() != null
                ? entity.getSeller().getName().trim()
                : "Market Merchant";
        String websiteUrl = entity.getSeller() != null ? entity.getSeller().getWebsiteUrl() : null;
        String logoUrl = entity.getSeller() != null ? entity.getSeller().getLogoUrl() : null;
        String offerUrl = entity.getProductUrl() != null ? entity.getProductUrl() : (websiteUrl != null ? websiteUrl : "#");

        OfferSourceDTO source = OfferSourceDTO.builder()
                .sellerId(sellerId)
                .sellerName(sellerName)
                .websiteUrl(websiteUrl)
                .logoUrl(logoUrl)
                .offerUrl(offerUrl)
                .build();

        BigDecimal basePrice = entity.getCurrentPrice();
        BigDecimal originalPrice = entity.getOriginalPrice() != null && entity.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0
                ? entity.getOriginalPrice()
                : null;

        BigDecimal discountPct = BigDecimal.ZERO;
        if (entity.getDiscountPercentage() != null && entity.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            discountPct = entity.getDiscountPercentage();
        } else if (originalPrice != null && originalPrice.compareTo(basePrice) > 0) {
            discountPct = originalPrice.subtract(basePrice)
                    .divide(originalPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        OfferPricingDTO pricing = OfferPricingDTO.builder()
                .basePrice(basePrice)
                .originalPrice(originalPrice)
                .discountPercentage(discountPct)
                .currency("USD")
                .shippingPrice(null)
                .taxesAndFees(null)
                .totalPrice(null)
                .basePriceOnly(true)
                .pricingDisclaimer("Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.")
                .build();

        FreshnessResult freshness = assessFreshness(entity.getLastUpdated());
        boolean isStale = freshness.isStale();
        LocalDateTime effectiveObservedAt = freshness.effectiveObservedAt();

        String trustLabel;
        String trustDesc;
        if (isStale) {
            trustLabel = "Catalog Offer (Stale)";
            trustDesc = "Catalog seed offer has exceeded the freshness window. Not a live verified retailer feed.";
        } else if (freshness.isUnknownFreshness()) {
            trustLabel = "Catalog Offer (Unverified Freshness)";
            trustDesc = "Catalog seed offer with unverified observation timestamp. Not a live verified retailer feed.";
        } else {
            trustLabel = "Catalog Offer";
            trustDesc = "Demonstration catalog seed offer. Not a live verified retailer feed.";
        }

        OfferProvenanceDTO provenance = OfferProvenanceDTO.builder()
                .sourceType(ProvenanceType.DEMO)
                .verificationStatus(VerificationStatus.UNVERIFIED) // Stale status does not imply a verified source
                .liveVerified(false)
                .observedAt(effectiveObservedAt)
                .rawLastUpdated(effectiveObservedAt != null ? effectiveObservedAt.toString() : null)
                .stale(isStale)
                .trustLabel(trustLabel)
                .trustDescription(trustDesc)
                .build();

        OfferAvailabilityDTO availability = OfferAvailabilityDTO.builder()
                .status(AvailabilityStatus.UNKNOWN)
                .available(null)
                .stockQuantity(null)
                .build();

        UUID productId = entity.getProduct() != null ? entity.getProduct().getId() : null;
        String productTitle = entity.getProduct() != null ? entity.getProduct().getName() : null;

        return NormalizedOfferDTO.builder()
                .id(entity.getId())
                .productId(productId)
                .productTitle(productTitle)
                .source(source)
                .pricing(pricing)
                .provenance(provenance)
                .availability(availability)
                .build();
    }

    /**
     * Normalizes a RawMarketplaceOffer from an external or mock provider into a NormalizedOfferDTO.
     * Enforces provenance rules:
     * - DEMO/MOCK providers are marked as DEMO, UNVERIFIED, liveVerified=false.
     * - Missing fees are never inferred as zero.
     * - Invalid or negative prices return null.
     */
    public NormalizedOfferDTO normalize(RawMarketplaceOffer raw, MarketplaceQueryContext context, ProvenanceType sourceType, String providerId) {
        if (raw == null) {
            return null;
        }

        // Price Safety Gate: Never convert an invalid, missing, or negative price into a valid zero-price offer.
        if (raw.price() == null || raw.price().compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        // Currency Correctness: Base prices are strictly ranked as canonical USD.
        // Missing, blank, non-USD, or unsupported currencies are rejected from canonical ranking
        // to prevent fabricating an assumption that a missing or non-USD currency is USD.
        if (raw.currency() == null || raw.currency().trim().isEmpty() || !"USD".equalsIgnoreCase(raw.currency().trim())) {
            return null;
        }
        String currency = "USD";

        UUID offerId = null;
        try {
            if (raw.rawOfferId() != null) {
                offerId = UUID.fromString(raw.rawOfferId());
            }
        } catch (IllegalArgumentException ignored) {
            // Non-UUID rawOfferId will generate a deterministic or pseudo-random UUID below
        }
        if (offerId == null) {
            offerId = UUID.randomUUID();
        }

        String sellerName = raw.sellerName() != null && !raw.sellerName().trim().isEmpty()
                ? raw.sellerName().trim()
                : "Marketplace Merchant";

        OfferSourceDTO source = OfferSourceDTO.builder()
                .sellerId(null)
                .sellerName(sellerName)
                .websiteUrl(raw.sellerUrl())
                .logoUrl(null)
                .offerUrl(raw.offerUrl() != null ? raw.offerUrl() : (raw.sellerUrl() != null ? raw.sellerUrl() : "#"))
                .providerName(providerId != null ? providerId : "marketplace")
                .build();

        BigDecimal basePrice = raw.price();
        BigDecimal originalPrice = raw.originalPrice() != null && raw.originalPrice().compareTo(BigDecimal.ZERO) > 0
                ? raw.originalPrice()
                : null;

        BigDecimal discountPct = BigDecimal.ZERO;
        if (originalPrice != null && originalPrice.compareTo(basePrice) > 0) {
            discountPct = originalPrice.subtract(basePrice)
                    .divide(originalPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        OfferPricingDTO pricing = OfferPricingDTO.builder()
                .basePrice(basePrice)
                .originalPrice(originalPrice)
                .discountPercentage(discountPct)
                .currency(currency)
                .shippingPrice(null)
                .taxesAndFees(null)
                .totalPrice(null)
                .basePriceOnly(true)
                .pricingDisclaimer("Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.")
                .build();

        FreshnessResult freshness = assessFreshness(raw.observationTime());
        boolean isStale = freshness.isStale();
        LocalDateTime effectiveObservedAt = freshness.effectiveObservedAt();

        ProvenanceType provType = sourceType != null ? sourceType : ProvenanceType.DEMO;
        VerificationStatus verStatus;
        boolean liveVerified;
        String trustLabel;
        String trustDesc;

        if (provType == ProvenanceType.DEMO) {
            verStatus = VerificationStatus.UNVERIFIED;
            liveVerified = false;
            if ("mock".equalsIgnoreCase(providerId)) {
                if (isStale) {
                    trustLabel = "Simulated Offer (Stale)";
                    trustDesc = "Simulated offline marketplace offer that has exceeded the freshness window.";
                } else if (freshness.isUnknownFreshness()) {
                    trustLabel = "Simulated Offer (Unverified Freshness)";
                    trustDesc = "Simulated offline marketplace offer with unverified observation timestamp.";
                } else {
                    trustLabel = "Simulated Offer";
                    trustDesc = "Simulated offline marketplace offer for testing. Not a live verified retailer feed.";
                }
            } else {
                if (isStale) {
                    trustLabel = "Catalog Offer (Stale)";
                    trustDesc = "Catalog seed offer has exceeded the freshness window. Not a live verified retailer feed.";
                } else if (freshness.isUnknownFreshness()) {
                    trustLabel = "Catalog Offer (Unverified Freshness)";
                    trustDesc = "Catalog seed offer with unverified observation timestamp. Not a live verified retailer feed.";
                } else {
                    trustLabel = "Catalog Offer";
                    trustDesc = "Demonstration catalog seed offer. Not a live verified retailer feed.";
                }
            }
        } else if (provType == ProvenanceType.LIVE) {
            verStatus = isStale ? VerificationStatus.STALE : VerificationStatus.VERIFIED;
            liveVerified = !isStale && !freshness.isUnknownFreshness();
            if (isStale) {
                trustLabel = "Live Verified (Stale)";
                trustDesc = "Live merchant endpoint observation that has exceeded the freshness window.";
            } else if (freshness.isUnknownFreshness()) {
                trustLabel = "Live Feed (Unverified Freshness)";
                trustDesc = "Live merchant feed with unverified observation timestamp.";
            } else {
                trustLabel = "Live Verified";
                trustDesc = "Real-time verified offer from integrated merchant endpoint.";
            }
        } else {
            verStatus = VerificationStatus.UNVERIFIED;
            liveVerified = false;
            if (isStale) {
                trustLabel = "Catalog Offer (Stale)";
                trustDesc = "Catalog offer that has exceeded the freshness window. Not a live verified retailer feed.";
            } else if (freshness.isUnknownFreshness()) {
                trustLabel = "Catalog Offer (Unverified Freshness)";
                trustDesc = "Catalog offer with unverified observation timestamp. Not a live verified retailer feed.";
            } else {
                trustLabel = "Catalog Offer";
                trustDesc = "Demonstration catalog offer. Not a live verified retailer feed.";
            }
        }

        OfferProvenanceDTO provenance = OfferProvenanceDTO.builder()
                .sourceType(provType)
                .verificationStatus(verStatus)
                .liveVerified(liveVerified)
                .observedAt(effectiveObservedAt)
                .rawLastUpdated(effectiveObservedAt != null ? effectiveObservedAt.toString() : null)
                .stale(isStale)
                .trustLabel(trustLabel)
                .trustDescription(trustDesc)
                .build();

        AvailabilityStatus availStatus = AvailabilityStatus.UNKNOWN;
        Boolean available = raw.inStock();
        if (available != null) {
            availStatus = available ? AvailabilityStatus.IN_STOCK : AvailabilityStatus.OUT_OF_STOCK;
        }

        OfferAvailabilityDTO availability = OfferAvailabilityDTO.builder()
                .status(availStatus)
                .available(available)
                .stockQuantity(raw.stockQuantity())
                .build();

        UUID productId = context != null ? context.productId() : null;
        String productTitle = context != null ? context.productName() : null;

        return NormalizedOfferDTO.builder()
                .id(offerId)
                .productId(productId)
                .productTitle(productTitle)
                .source(source)
                .pricing(pricing)
                .provenance(provenance)
                .availability(availability)
                .build();
    }

    /**
     * Normalizes a collection of offer DTOs for a product, ordering them by lowest base price
     * and computing competitive ranking and savings.
     */
    public List<NormalizedOfferDTO> normalizeOffers(List<ProductPriceResponseDTO> dtos, String productTitle) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }

        List<NormalizedOfferDTO> offers = new ArrayList<>();
        for (ProductPriceResponseDTO dto : dtos) {
            NormalizedOfferDTO offer = normalize(dto);
            if (offer != null) {
                if (productTitle != null && offer.getProductTitle() == null) {
                    offer.setProductTitle(productTitle);
                }
                offers.add(offer);
            }
        }

        return rankAndEvaluateOffers(offers);
    }

    /**
     * Ranks pre-normalized offers by canonical USD base price, computes best deal, savings,
     * and price differences from lowest. Rejects any invalid offers.
     */
    public List<NormalizedOfferDTO> rankAndEvaluateOffers(List<NormalizedOfferDTO> offers) {
        if (offers == null || offers.isEmpty()) {
            return Collections.emptyList();
        }

        List<NormalizedOfferDTO> validOffers = new ArrayList<>();
        for (NormalizedOfferDTO offer : offers) {
            if (offer != null && offer.getPricing() != null
                    && offer.getPricing().getBasePrice() != null
                    && offer.getPricing().getBasePrice().compareTo(BigDecimal.ZERO) >= 0) {
                validOffers.add(offer);
            }
        }

        if (validOffers.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by base price ascending, tie-breaking by higher discount then seller name
        validOffers.sort((a, b) -> {
            int cmp = a.getPricing().getBasePrice().compareTo(b.getPricing().getBasePrice());
            if (cmp != 0) return cmp;
            BigDecimal discA = a.getPricing().getDiscountPercentage() != null ? a.getPricing().getDiscountPercentage() : BigDecimal.ZERO;
            BigDecimal discB = b.getPricing().getDiscountPercentage() != null ? b.getPricing().getDiscountPercentage() : BigDecimal.ZERO;
            int discCmp = discB.compareTo(discA);
            if (discCmp != 0) return discCmp;
            String nameA = (a.getSource() != null && a.getSource().getSellerName() != null) ? a.getSource().getSellerName() : "";
            String nameB = (b.getSource() != null && b.getSource().getSellerName() != null) ? b.getSource().getSellerName() : "";
            return nameA.compareTo(nameB);
        });

        BigDecimal lowestBasePrice = validOffers.get(0).getPricing().getBasePrice();

        for (int i = 0; i < validOffers.size(); i++) {
            NormalizedOfferDTO offer = validOffers.get(i);
            offer.setIsBestDeal(i == 0);
            BigDecimal orig = offer.getPricing().getOriginalPrice();
            BigDecimal base = offer.getPricing().getBasePrice();
            if (orig != null && orig.compareTo(base) > 0) {
                offer.setSavings(orig.subtract(base));
            } else {
                offer.setSavings(BigDecimal.ZERO);
            }
            offer.setPriceDifferenceFromLowest(base.subtract(lowestBasePrice));
        }

        return validOffers;
    }
}
