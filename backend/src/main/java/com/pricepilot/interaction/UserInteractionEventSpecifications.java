package com.pricepilot.interaction;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserInteractionEventSpecifications {

    public static Specification<UserInteractionEventEntity> withFilters(
            UUID userId,
            UUID productId,
            UUID sellerId,
            InteractionType interactionType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search) {

        return (root, query, cb) -> {
            // Eagerly fetch user, product, and seller relations for results (prevents N+1)
            // Skip fetch join for count queries (used by Spring Data Pageable)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("product", JoinType.LEFT);
                root.fetch("seller", JoinType.LEFT);
            }

            var predicate = cb.conjunction();

            if (userId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("user").get("id"), userId));
            }

            if (productId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("product").get("id"), productId));
            }

            if (sellerId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("seller").get("id"), sellerId));
            }

            if (interactionType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("interactionType"), interactionType));
            }

            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                
                // 1. Search in metadata JSON (cast jsonb to text)
                var metadataCast = cb.function("cast", String.class, root.get("metadata"), cb.literal("text"));
                var searchInMetadata = cb.like(cb.lower(metadataCast), searchPattern);
                
                // 2. Search in user email
                var searchInUserEmail = cb.like(cb.lower(root.join("user", JoinType.LEFT).get("email")), searchPattern);
                
                // 3. Search in product name
                var searchInProductName = cb.like(cb.lower(root.join("product", JoinType.LEFT).get("name")), searchPattern);
                
                // 4. Search in seller name
                var searchInSellerName = cb.like(cb.lower(root.join("seller", JoinType.LEFT).get("name")), searchPattern);
                
                var searchPredicate = cb.or(searchInMetadata, searchInUserEmail, searchInProductName, searchInSellerName);
                predicate = cb.and(predicate, searchPredicate);
            }

            return predicate;
        };
    }
}
