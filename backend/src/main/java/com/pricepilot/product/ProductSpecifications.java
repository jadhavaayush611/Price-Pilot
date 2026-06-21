package com.pricepilot.product;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecifications {

    public static Specification<ProductEntity> withFiltersAndCustomSort(
            String keyword, String category, String brand, String customSortField, Sort.Direction direction) {
        
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword search (using PostgreSQL Full Text Search GIN index)
            if (keyword != null && !keyword.trim().isEmpty()) {
                Predicate ftsMatch = cb.isTrue(
                    cb.function("fts_match", Boolean.class, root.get("searchVector"), cb.literal(keyword.trim()))
                );
                predicates.add(ftsMatch);

                // Add FTS-based ranking support to default search order if sorting isn't explicitly configured
                if (customSortField == null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                    Expression<Double> rankExpr = cb.function("fts_rank", Double.class, root.get("searchVector"), cb.literal(keyword.trim()));
                    query.orderBy(cb.desc(rankExpr));
                }
            }

            // 2. Category filter (case-insensitive)
            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            // 3. Brand filter (case-insensitive)
            if (brand != null && !brand.trim().isEmpty() && !brand.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            // 4. Custom sorting (by price or discount)
            if (customSortField != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Perform a left join to retrieve prices
                Join<ProductEntity, ?> priceJoin = root.join("productPrices", JoinType.LEFT);
                
                // Group by product id to prevent duplicate rows returned by the join
                query.groupBy(root.get("id"));

                if ("price".equalsIgnoreCase(customSortField)) {
                    Expression<BigDecimal> minPrice = cb.min(priceJoin.get("currentPrice"));
                    if (direction == Sort.Direction.ASC) {
                        query.orderBy(cb.asc(minPrice));
                    } else {
                        query.orderBy(cb.desc(minPrice));
                    }
                } else if ("discount".equalsIgnoreCase(customSortField)) {
                    Expression<BigDecimal> maxDiscount = cb.max(priceJoin.get("discountPercentage"));
                    if (direction == Sort.Direction.ASC) {
                        query.orderBy(cb.asc(maxDiscount));
                    } else {
                        query.orderBy(cb.desc(maxDiscount));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
