package com.pricepilot.dataset;

import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatasetSpecifications {

    public static Specification<ProductEntity> productSpec(
            String category, String brand, Boolean archived, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (brand != null && !brand.trim().isEmpty() && !brand.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            if (archived != null) {
                predicates.add(cb.equal(root.get("archived"), archived));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<ProductAnalyticsEntity> analyticsSpec(
            UUID productId, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<PriceWatchlistEntity> watchlistSpec(
            UUID userId, UUID productId, Boolean active, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SavedProductEntity> savedProductSpec(
            UUID userId, UUID productId, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("id").get("userId"), userId));
            }

            if (productId != null) {
                predicates.add(cb.equal(root.get("id").get("productId"), productId));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<UserEntity> userSpec(
            UUID userId, String role, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("id"), userId));
            }

            if (role != null && !role.trim().isEmpty()) {
                try {
                    com.pricepilot.user.Role roleEnum = com.pricepilot.user.Role.valueOf(role.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("role"), roleEnum));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid roles or add a predicate that always fails
                    predicates.add(cb.disjunction());
                }
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<PriceHistoryEntity> priceHistorySpec(
            UUID productId, UUID sellerId, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }

            if (sellerId != null) {
                predicates.add(cb.equal(root.get("seller").get("id"), sellerId));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
