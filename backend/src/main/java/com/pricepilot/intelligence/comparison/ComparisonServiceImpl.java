package com.pricepilot.intelligence.comparison;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.ComparisonRow;
import com.pricepilot.intelligence.comparison.entity.ComparisonSessionEntity;
import com.pricepilot.intelligence.comparison.repository.ComparisonSessionRepository;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Foundation implementation of ComparisonService for PricePilot v1.1.
 */
@Service
public class ComparisonServiceImpl implements ComparisonService {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ComparisonSessionRepository comparisonSessionRepository;
    private final SavedComparisonRepository savedComparisonRepository;

    public ComparisonServiceImpl(
            ProductRepository productRepository,
            ProductService productService,
            ComparisonSessionRepository comparisonSessionRepository,
            SavedComparisonRepository savedComparisonRepository) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.comparisonSessionRepository = comparisonSessionRepository;
        this.savedComparisonRepository = savedComparisonRepository;
    }

    @Override
    @Transactional
    public ComparisonResponse compareProducts(ComparisonRequest request) {
        List<UUID> productIds = request.getProductIds() != null ? request.getProductIds() : Collections.emptyList();
        return buildComparisonResponse(productIds);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonResponse compareProducts(List<UUID> productIds) {
        return buildComparisonResponse(productIds);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonResponse getComparisonSession(UUID sessionId) {
        ComparisonSessionEntity session = comparisonSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Comparison session not found with ID: " + sessionId));

        List<UUID> productIds = parseProductIds(session.getProductIds());
        return buildComparisonResponse(productIds, session.getId());
    }

    @Override
    @Transactional
    public ComparisonResponse saveComparisonSession(UUID userId, ComparisonRequest request) {
        ComparisonSessionEntity session = new ComparisonSessionEntity();
        session.setUserId(userId);
        session.setSessionToken(UUID.randomUUID().toString());
        session.setProductIds(formatProductIds(request.getProductIds()));
        session.setTitle(request.getCategory() != null ? request.getCategory() + " Comparison" : "Product Comparison");
        session = comparisonSessionRepository.save(session);

        return buildComparisonResponse(request.getProductIds(), session.getId());
    }

    private ComparisonResponse buildComparisonResponse(List<UUID> productIds) {
        return buildComparisonResponse(productIds, UUID.randomUUID());
    }

    private ComparisonResponse buildComparisonResponse(List<UUID> productIds, UUID comparisonId) {
        if (productIds == null || productIds.isEmpty()) {
            return new ComparisonResponse(comparisonId, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), "No products selected for comparison.", LocalDateTime.now());
        }

        List<ProductResponseDTO> products = productIds.stream()
                .map(id -> {
                    try {
                        return productService.getProductById(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ComparisonRow> rows = generateComparisonRows(products);
        Map<UUID, ProductScore> scores = generateProductScores(products);
        String summary = String.format("Comparing %d products across pricing, brand authority, and deal quality.", products.size());

        return new ComparisonResponse(comparisonId, products, rows, scores, summary, LocalDateTime.now());
    }

    private List<ComparisonRow> generateComparisonRows(List<ProductResponseDTO> products) {
        List<ComparisonRow> rows = new ArrayList<>();

        Map<UUID, String> brandMap = new HashMap<>();
        Map<UUID, String> categoryMap = new HashMap<>();
        Map<UUID, String> priceMap = new HashMap<>();
        Map<UUID, String> sellersCountMap = new HashMap<>();

        for (ProductResponseDTO p : products) {
            brandMap.put(p.getId(), p.getBrand());
            categoryMap.put(p.getId(), p.getCategory());
            java.math.BigDecimal lowest = getLowestPriceFromDto(p);
            priceMap.put(p.getId(), lowest != null ? "$" + lowest : "N/A");
            sellersCountMap.put(p.getId(), p.getPrices() != null ? String.valueOf(p.getPrices().size()) : "0");
        }

        rows.add(new ComparisonRow("Brand", "General", brandMap, false));
        rows.add(new ComparisonRow("Category", "General", categoryMap, false));
        rows.add(new ComparisonRow("Best Price", "Pricing", priceMap, true));
        rows.add(new ComparisonRow("Seller Offers", "Availability", sellersCountMap, false));

        return rows;
    }

    private Map<UUID, ProductScore> generateProductScores(List<ProductResponseDTO> products) {
        Map<UUID, ProductScore> scores = new HashMap<>();
        for (ProductResponseDTO p : products) {
            java.math.BigDecimal lowest = getLowestPriceFromDto(p);
            double priceScore = lowest != null ? Math.max(50.0, 100.0 - (lowest.doubleValue() / 20.0)) : 70.0;
            double popularity = 85.0;
            double overall = (priceScore + popularity) / 2.0;

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("PriceValue", priceScore);
            breakdown.put("Popularity", popularity);

            scores.put(p.getId(), new ProductScore(
                    p.getId(),
                    p.getName(),
                    overall,
                    priceScore,
                    80.0,
                    popularity,
                    breakdown,
                    overall > 80.0 ? "TOP PICK" : "VALUE OPTION"
            ));
        }
        return scores;
    }

    private java.math.BigDecimal getLowestPriceFromDto(ProductResponseDTO dto) {
        if (dto == null || dto.getPrices() == null || dto.getPrices().isEmpty()) {
            return null;
        }
        return dto.getPrices().stream()
                .map(com.pricepilot.productprice.dto.ProductPriceResponseDTO::getCurrentPrice)
                .filter(Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(null);
    }

    private List<UUID> parseProductIds(String productIdsStr) {
        if (productIdsStr == null || productIdsStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(productIdsStr.split(","))
                .map(String::trim)
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private String formatProductIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return "";
        }
        return productIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }
}
