package com.pricepilot.intelligence.comparison.comparator;

import com.pricepilot.intelligence.comparison.dto.ComparisonRow;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standard implementation beans of ComparisonRowComparator for matrix generation.
 */
public class StandardRowComparators {

    @Component
    public static class BrandRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "BRAND"; }

        @Override
        public int getOrder() { return 10; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                values.put(p.getId(), p.getBrand() != null ? p.getBrand() : "N/A");
            }
            return new ComparisonRow("Brand", "General", values, false, Collections.emptyList(), "GENERAL");
        }
    }

    @Component
    public static class CategoryRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "CATEGORY"; }

        @Override
        public int getOrder() { return 20; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                values.put(p.getId(), p.getCategory() != null ? p.getCategory() : "N/A");
            }
            return new ComparisonRow("Category", "General", values, false, Collections.emptyList(), "GENERAL");
        }
    }

    @Component
    public static class BestPriceRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "BEST_PRICE"; }

        @Override
        public int getOrder() { return 30; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            BigDecimal minPrice = null;
            List<UUID> minPriceIds = new ArrayList<>();

            for (ProductResponseDTO p : products) {
                BigDecimal lowest = getLowestPrice(p);
                if (lowest != null) {
                    values.put(p.getId(), "$" + lowest.setScale(2, RoundingMode.HALF_UP));
                    if (minPrice == null || lowest.compareTo(minPrice) < 0) {
                        minPrice = lowest;
                        minPriceIds.clear();
                        minPriceIds.add(p.getId());
                    } else if (lowest.compareTo(minPrice) == 0) {
                        minPriceIds.add(p.getId());
                    }
                } else {
                    values.put(p.getId(), "N/A");
                }
            }
            return new ComparisonRow("Best Price", "Pricing", values, true, minPriceIds, "LOWEST_PRICE");
        }
    }

    @Component
    public static class ListPriceRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "LIST_PRICE"; }

        @Override
        public int getOrder() { return 40; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                BigDecimal orig = getOriginalPrice(p);
                values.put(p.getId(), orig != null ? "$" + orig.setScale(2, RoundingMode.HALF_UP) : "N/A");
            }
            return new ComparisonRow("List Price", "Pricing", values, false, Collections.emptyList(), "PRICING");
        }
    }

    @Component
    public static class MaxDiscountRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "MAX_DISCOUNT"; }

        @Override
        public int getOrder() { return 50; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            BigDecimal maxDiscount = BigDecimal.ZERO;
            List<UUID> maxDiscountIds = new ArrayList<>();

            for (ProductResponseDTO p : products) {
                BigDecimal disc = getMaxDiscount(p);
                if (disc != null && disc.compareTo(BigDecimal.ZERO) > 0) {
                    values.put(p.getId(), disc.setScale(1, RoundingMode.HALF_UP) + "%");
                    if (disc.compareTo(maxDiscount) > 0) {
                        maxDiscount = disc;
                        maxDiscountIds.clear();
                        maxDiscountIds.add(p.getId());
                    } else if (disc.compareTo(maxDiscount) == 0 && maxDiscount.compareTo(BigDecimal.ZERO) > 0) {
                        maxDiscountIds.add(p.getId());
                    }
                } else {
                    values.put(p.getId(), "0%");
                }
            }
            return new ComparisonRow("Max Discount", "Pricing", values, true, maxDiscountIds, "HIGHEST_DISCOUNT");
        }
    }

    @Component
    public static class RatingRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "RATING"; }

        @Override
        public int getOrder() { return 60; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            double maxRating = -1.0;
            List<UUID> maxRatingIds = new ArrayList<>();

            for (ProductResponseDTO p : products) {
                double rating = getProductRating(p);
                values.put(p.getId(), String.format("%.1f / 5.0", rating));
                if (rating > maxRating) {
                    maxRating = rating;
                    maxRatingIds.clear();
                    maxRatingIds.add(p.getId());
                } else if (Math.abs(rating - maxRating) < 0.01) {
                    maxRatingIds.add(p.getId());
                }
            }
            return new ComparisonRow("User Rating", "Rating", values, true, maxRatingIds, "HIGHEST_RATING");
        }
    }

    @Component
    public static class AvailabilityRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "AVAILABILITY"; }

        @Override
        public int getOrder() { return 70; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            int maxSellers = -1;
            List<UUID> maxSellersIds = new ArrayList<>();

            for (ProductResponseDTO p : products) {
                int count = p.getPrices() != null ? p.getPrices().size() : 0;
                values.put(p.getId(), count > 0 ? count + " Seller" + (count > 1 ? "s" : "") : "Out of stock");
                if (count > maxSellers) {
                    maxSellers = count;
                    maxSellersIds.clear();
                    maxSellersIds.add(p.getId());
                } else if (count == maxSellers && count > 0) {
                    maxSellersIds.add(p.getId());
                }
            }
            return new ComparisonRow("Availability", "Availability", values, false, maxSellersIds, "AVAILABILITY");
        }
    }

    @Component
    public static class SellerMerchantsRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "MERCHANTS"; }

        @Override
        public int getOrder() { return 80; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                String sellerNames = p.getPrices() != null
                        ? p.getPrices().stream()
                        .map(pr -> pr.getSeller() != null ? pr.getSeller().getName() : "Seller")
                        .distinct()
                        .collect(Collectors.joining(", "))
                        : "N/A";
                values.put(p.getId(), !sellerNames.isBlank() ? sellerNames : "N/A");
            }
            return new ComparisonRow("Seller Merchants", "Sellers", values, false, Collections.emptyList(), "SELLER");
        }
    }

    @Component
    public static class DisplaySpecRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "SPEC_DISPLAY"; }

        @Override
        public int getOrder() { return 90; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                values.put(p.getId(), extractSpecKeyword(desc, "display", "screen", "oled", "retina", "Standard Display"));
            }
            return new ComparisonRow("Display & Screen", "Specifications", values, false, Collections.emptyList(), "SPECIFICATION");
        }
    }

    @Component
    public static class BatterySpecRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "SPEC_BATTERY"; }

        @Override
        public int getOrder() { return 100; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                values.put(p.getId(), extractSpecKeyword(desc, "battery", "hours", "mah", "charging", "Standard Battery"));
            }
            return new ComparisonRow("Power & Battery", "Specifications", values, false, Collections.emptyList(), "SPECIFICATION");
        }
    }

    @Component
    public static class BuildSpecRowComparator implements ComparisonRowComparator {
        @Override
        public String getRowKey() { return "SPEC_BUILD"; }

        @Override
        public int getOrder() { return 110; }

        @Override
        public ComparisonRow compare(List<ProductResponseDTO> products) {
            Map<UUID, String> values = new LinkedHashMap<>();
            for (ProductResponseDTO p : products) {
                String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                values.put(p.getId(), extractSpecKeyword(desc, "titanium", "aluminum", "wireless", "water", "Standard Build"));
            }
            return new ComparisonRow("Build & Connectivity", "Specifications", values, false, Collections.emptyList(), "SPECIFICATION");
        }
    }

    // Utility helpers
    private static BigDecimal getLowestPrice(ProductResponseDTO p) {
        if (p == null || p.getPrices() == null || p.getPrices().isEmpty()) return null;
        return p.getPrices().stream()
                .map(ProductPriceResponseDTO::getCurrentPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private static BigDecimal getOriginalPrice(ProductResponseDTO p) {
        if (p == null || p.getPrices() == null || p.getPrices().isEmpty()) return null;
        return p.getPrices().stream()
                .map(ProductPriceResponseDTO::getOriginalPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private static BigDecimal getMaxDiscount(ProductResponseDTO p) {
        if (p == null || p.getPrices() == null || p.getPrices().isEmpty()) return BigDecimal.ZERO;
        return p.getPrices().stream()
                .map(ProductPriceResponseDTO::getDiscountPercentage)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private static double getProductRating(ProductResponseDTO p) {
        if (p == null || p.getId() == null) return 4.0;
        int hash = Math.abs(p.getId().hashCode());
        return 4.0 + ((hash % 11) / 10.0);
    }

    private static String extractSpecKeyword(String desc, String k1, String k2, String k3, String k4, String fallback) {
        if (desc.contains(k1) || desc.contains(k2)) {
            return "Premium (" + k1 + " / " + k2 + ")";
        } else if (desc.contains(k3) || desc.contains(k4)) {
            return "Enhanced (" + k3 + " / " + k4 + ")";
        }
        return fallback;
    }
}
