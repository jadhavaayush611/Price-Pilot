package com.pricepilot.intelligence.discovery.specification;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.productprice.ProductPriceEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic JPA specifications for intelligent candidate retrieval and database-side filtering.
 */
public class DiscoverySpecifications {

    public static Specification<ProductEntity> buildDiscoverySpec(
            List<String> keywords,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            UUID sellerId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Always exclude archived products
            predicates.add(cb.isFalse(root.get("archived")));

            // 2. Keyword tokens matching across name, brand, category, description
            if (keywords != null && !keywords.isEmpty()) {
                List<Predicate> tokenPredicates = new ArrayList<>();
                for (String token : keywords) {
                    if (token == null || token.trim().isEmpty()) continue;
                    String pattern = "%" + token.trim().toLowerCase() + "%";
                    Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                    Predicate brandMatch = cb.like(cb.lower(root.get("brand")), pattern);
                    Predicate categoryMatch = cb.like(cb.lower(root.get("category")), pattern);
                    Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                    tokenPredicates.add(cb.or(nameMatch, brandMatch, categoryMatch, descMatch));
                }
                if (!tokenPredicates.isEmpty()) {
                    predicates.add(cb.and(tokenPredicates.toArray(new Predicate[0])));
                }
            }

            // 3. Category filter
            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            // 4. Brand filter
            if (brand != null && !brand.trim().isEmpty() && !brand.equalsIgnoreCase("All")) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            // 5. Price bounds or In-Stock or Seller filters (requires subquery on ProductPriceEntity)
            boolean needsPriceSubquery = minPrice != null || maxPrice != null || Boolean.TRUE.equals(inStock) || sellerId != null;
            if (needsPriceSubquery) {
                Subquery<UUID> priceSubquery = query.subquery(UUID.class);
                Root<ProductPriceEntity> priceRoot = priceSubquery.from(ProductPriceEntity.class);
                priceSubquery.select(priceRoot.get("product").get("id"));

                List<Predicate> pricePreds = new ArrayList<>();
                pricePreds.add(cb.equal(priceRoot.get("product").get("id"), root.get("id")));

                if (minPrice != null) {
                    pricePreds.add(cb.greaterThanOrEqualTo(priceRoot.get("currentPrice"), minPrice));
                }
                if (maxPrice != null) {
                    pricePreds.add(cb.lessThanOrEqualTo(priceRoot.get("currentPrice"), maxPrice));
                }
                if (sellerId != null) {
                    pricePreds.add(cb.equal(priceRoot.get("seller").get("id"), sellerId));
                }

                priceSubquery.where(pricePreds.toArray(new Predicate[0]));
                predicates.add(cb.exists(priceSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
