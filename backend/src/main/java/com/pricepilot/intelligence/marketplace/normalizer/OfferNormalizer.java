package com.pricepilot.intelligence.marketplace.normalizer;

import com.pricepilot.intelligence.marketplace.dto.*;
import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Normalizes raw and DTO product price representations into NormalizedOfferDTOs.
 * Strictly adheres to the Marketplace Data Integrity Contract:
 * - Missing shipping or checkout fees are NEVER inferred as zero.
 * - Catalog seed and demo data is labeled unverified; never claimed as live verified.
 * - Stock states are never fabricated.
 */
@Component
public class OfferNormalizer {

    private static final int DEFAULT_STALE_THRESHOLD_HOURS = 168; // 7 days

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

        LocalDateTime observedAt = dto.getLastUpdated();
        boolean isStale = false;
        if (observedAt != null) {
            LocalDateTime now = LocalDateTime.now();
            if (observedAt.isBefore(now)) {
                long hours = ChronoUnit.HOURS.between(observedAt, now);
                if (hours > DEFAULT_STALE_THRESHOLD_HOURS) {
                    isStale = true;
                }
            }
        }

        OfferProvenanceDTO provenance = OfferProvenanceDTO.builder()
                .sourceType(ProvenanceType.DEMO)
                .verificationStatus(VerificationStatus.UNVERIFIED) // Stale status does not imply a verified source
                .liveVerified(false) // Demo/seed data is never live verified
                .observedAt(observedAt)
                .rawLastUpdated(observedAt != null ? observedAt.toString() : null)
                .stale(isStale)
                .trustLabel(isStale ? "Catalog Offer (Stale)" : "Catalog Offer")
                .trustDescription(isStale
                        ? "Catalog seed offer has exceeded the freshness window. Not a live verified retailer feed."
                        : "Demonstration catalog seed offer. Not a live verified retailer feed.")
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

        LocalDateTime observedAt = entity.getLastUpdated();
        boolean isStale = false;
        if (observedAt != null) {
            LocalDateTime now = LocalDateTime.now();
            if (observedAt.isBefore(now)) {
                long hours = ChronoUnit.HOURS.between(observedAt, now);
                if (hours > DEFAULT_STALE_THRESHOLD_HOURS) {
                    isStale = true;
                }
            }
        }

        OfferProvenanceDTO provenance = OfferProvenanceDTO.builder()
                .sourceType(ProvenanceType.DEMO)
                .verificationStatus(VerificationStatus.UNVERIFIED) // Stale status does not imply a verified source
                .liveVerified(false)
                .observedAt(observedAt)
                .rawLastUpdated(observedAt != null ? observedAt.toString() : null)
                .stale(isStale)
                .trustLabel(isStale ? "Catalog Offer (Stale)" : "Catalog Offer")
                .trustDescription(isStale
                        ? "Catalog seed offer has exceeded the freshness window. Not a live verified retailer feed."
                        : "Demonstration catalog seed offer. Not a live verified retailer feed.")
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

        if (offers.isEmpty()) {
            return offers;
        }

        // Sort by base price ascending, tie-breaking by higher discount then seller name
        offers.sort((a, b) -> {
            int cmp = a.getPricing().getBasePrice().compareTo(b.getPricing().getBasePrice());
            if (cmp != 0) return cmp;
            int discCmp = b.getPricing().getDiscountPercentage().compareTo(a.getPricing().getDiscountPercentage());
            if (discCmp != 0) return discCmp;
            return a.getSource().getSellerName().compareTo(b.getSource().getSellerName());
        });

        BigDecimal lowestBasePrice = offers.get(0).getPricing().getBasePrice();

        for (int i = 0; i < offers.size(); i++) {
            NormalizedOfferDTO offer = offers.get(i);
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

        return offers;
    }
}
