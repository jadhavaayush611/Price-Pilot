package com.pricepilot.intelligence.comparison;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.comparison.comparator.ComparisonRowRegistry;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.ComparisonRow;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import com.pricepilot.intelligence.comparison.entity.ComparisonSessionEntity;
import com.pricepilot.intelligence.comparison.entity.SavedComparisonEntity;
import com.pricepilot.intelligence.comparison.repository.ComparisonSessionRepository;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-ready implementation of ComparisonService for PricePilot v1.1.
 * Supports side-by-side comparison of 2 to 5 products with modular scoring,
 * extensible feature matrix generation via ComparisonRowRegistry, tie handling,
 * missing attribute handling, and persistence.
 */
@Service
public class ComparisonServiceImpl implements ComparisonService {

    private final ProductRepository productRepository;
    private final ComparisonSessionRepository comparisonSessionRepository;
    private final SavedComparisonRepository savedComparisonRepository;
    private final ComparisonScoringStrategy scoringStrategy;
    private final ComparisonRowRegistry rowRegistry;

    public ComparisonServiceImpl(
            ProductRepository productRepository,
            ComparisonSessionRepository comparisonSessionRepository,
            SavedComparisonRepository savedComparisonRepository,
            ComparisonScoringStrategy scoringStrategy,
            ComparisonRowRegistry rowRegistry) {
        this.productRepository = productRepository;
        this.comparisonSessionRepository = comparisonSessionRepository;
        this.savedComparisonRepository = savedComparisonRepository;
        this.scoringStrategy = scoringStrategy;
        this.rowRegistry = rowRegistry;
    }

    @Override
    @Transactional
    public ComparisonResponse compareProducts(ComparisonRequest request) {
        List<UUID> productIds = request != null && request.getProductIds() != null
                ? request.getProductIds()
                : Collections.emptyList();
        return buildComparisonResponse(productIds);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonResponse compareProducts(List<UUID> productIds) {
        return buildComparisonResponse(productIds);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonResponse getComparisonSession(UUID sessionId, UUID authenticatedUserId) {
        Optional<SavedComparisonEntity> savedOpt = savedComparisonRepository.findById(sessionId);
        if (savedOpt.isPresent()) {
            SavedComparisonEntity saved = savedOpt.get();
            if (saved.getUserId() != null && !saved.getUserId().equals(authenticatedUserId)) {
                throw new AccessDeniedException("Unauthorized access to saved comparison ID: " + sessionId);
            }
            List<UUID> productIds = parseProductIds(saved.getProductIds());
            return buildComparisonResponse(productIds, saved.getId());
        }

        ComparisonSessionEntity session = comparisonSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Comparison session not found with ID: " + sessionId));

        if (session.getUserId() != null && authenticatedUserId != null && !session.getUserId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("Unauthorized access to comparison session ID: " + sessionId);
        }

        List<UUID> productIds = parseProductIds(session.getProductIds());
        return buildComparisonResponse(productIds, session.getId());
    }

    @Override
    @Transactional
    public SavedComparisonResponseDTO saveComparisonSession(UUID userId, ComparisonRequest request) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to save comparisons");
        }

        List<UUID> productIds = request.getProductIds() != null ? request.getProductIds() : Collections.emptyList();
        String formattedIds = formatProductIds(productIds);

        ComparisonSessionEntity session = new ComparisonSessionEntity();
        session.setUserId(userId);
        session.setSessionToken(UUID.randomUUID().toString());
        session.setProductIds(formattedIds);
        session.setTitle(request.getName() != null && !request.getName().isBlank()
                ? request.getName()
                : (request.getCategory() != null ? request.getCategory() + " Comparison" : "Product Comparison"));
        session = comparisonSessionRepository.save(session);

        SavedComparisonEntity saved = new SavedComparisonEntity();
        saved.setUserId(userId);
        saved.setSessionId(session.getId());
        saved.setName(session.getTitle());
        saved.setProductIds(formattedIds);
        saved.setNotes(request.getNotes());
        saved = savedComparisonRepository.save(saved);

        List<ProductResponseDTO> products = getProductDtosBatch(productIds);

        return new SavedComparisonResponseDTO(
                saved.getId(),
                saved.getUserId(),
                saved.getSessionId(),
                saved.getName(),
                productIds,
                saved.getNotes(),
                saved.getCreatedAt(),
                products
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedComparisonResponseDTO> getSavedComparisons(UUID userId, int page, int size) {
        return getSavedComparisons(userId, page, size, "createdAt", "desc", null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedComparisonResponseDTO> getSavedComparisons(UUID userId, int page, int size, String sortKey, String sortDir, String search) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to view saved comparisons");
        }

        String validSortKey = ("name".equalsIgnoreCase(sortKey)) ? "name" : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, Math.min(50, Math.max(1, size)), Sort.by(direction, validSortKey));

        Page<SavedComparisonEntity> savedEntities = savedComparisonRepository.findByUserIdAndSearch(userId, search, pageRequest);

        return savedEntities.map(entity -> {
            List<UUID> ids = parseProductIds(entity.getProductIds());
            List<ProductResponseDTO> products = getProductDtosBatch(ids);
            return new SavedComparisonResponseDTO(
                    entity.getId(),
                    entity.getUserId(),
                    entity.getSessionId(),
                    entity.getName(),
                    ids,
                    entity.getNotes(),
                    entity.getCreatedAt(),
                    products
            );
        });
    }

    @Override
    @Transactional
    public void deleteSavedComparison(UUID userId, UUID comparisonId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to delete saved comparisons");
        }

        Optional<SavedComparisonEntity> savedOpt = savedComparisonRepository.findById(comparisonId);
        if (savedOpt.isPresent()) {
            SavedComparisonEntity saved = savedOpt.get();
            if (!saved.getUserId().equals(userId)) {
                throw new AccessDeniedException("Unauthorized deletion attempt for saved comparison ID: " + comparisonId);
            }
            savedComparisonRepository.delete(saved);
            return;
        }

        Optional<ComparisonSessionEntity> sessionOpt = comparisonSessionRepository.findById(comparisonId);
        if (sessionOpt.isPresent()) {
            ComparisonSessionEntity session = sessionOpt.get();
            if (session.getUserId() != null && !session.getUserId().equals(userId)) {
                throw new AccessDeniedException("Unauthorized deletion attempt for comparison session ID: " + comparisonId);
            }
            comparisonSessionRepository.delete(session);
            return;
        }

        throw new ResourceNotFoundException("Saved comparison or session not found with ID: " + comparisonId);
    }

    private ComparisonResponse buildComparisonResponse(List<UUID> productIds) {
        return buildComparisonResponse(productIds, UUID.randomUUID());
    }

    private ComparisonResponse buildComparisonResponse(List<UUID> productIds, UUID comparisonId) {
        if (productIds == null || productIds.isEmpty()) {
            return new ComparisonResponse(
                    comparisonId,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    "No products selected for comparison.",
                    LocalDateTime.now()
            );
        }

        List<UUID> trimmedIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        List<ProductResponseDTO> products = getProductDtosBatch(trimmedIds);

        if (products.isEmpty()) {
            return new ComparisonResponse(
                    comparisonId,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    "No active products found matching the requested IDs.",
                    LocalDateTime.now()
            );
        }

        // Generate matrix rows using ComparisonRowRegistry pattern
        List<ComparisonRow> rows = rowRegistry.generateRows(products);
        Map<UUID, ProductScore> scores = scoringStrategy.calculateScores(products);
        String summary = generateComparisonSummary(products, scores);

        return new ComparisonResponse(comparisonId, products, rows, scores, summary, LocalDateTime.now());
    }

    private List<ProductResponseDTO> getProductDtosBatch(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductEntity> entities = productRepository.findAllByIdInWithPricesAndSellers(productIds);
        Map<UUID, ProductEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(ProductEntity::getId, e -> e, (e1, e2) -> e1));

        List<ProductResponseDTO> result = new ArrayList<>();
        for (UUID id : productIds) {
            ProductEntity entity = entityMap.get(id);
            if (entity != null && !entity.isArchived()) {
                ProductResponseDTO dto = ProductResponseDTO.fromEntity(entity);
                if (entity.getProductPrices() != null && !entity.getProductPrices().isEmpty()) {
                    List<ProductPriceResponseDTO> priceDTOs = entity.getProductPrices().stream()
                            .map(ProductPriceResponseDTO::fromEntity)
                            .collect(Collectors.toList());
                    dto.setPrices(priceDTOs);
                } else {
                    dto.setPrices(Collections.emptyList());
                }
                result.add(dto);
            }
        }
        return result;
    }

    private String generateComparisonSummary(List<ProductResponseDTO> products, Map<UUID, ProductScore> scores) {
        if (products.size() < 2) {
            return String.format("Showing specs for %s.", products.get(0).getName());
        }

        ProductResponseDTO topProduct = products.stream()
                .max(Comparator.comparingDouble(p -> scores.containsKey(p.getId()) ? scores.get(p.getId()).getOverallScore() : 0.0))
                .orElse(products.get(0));

        ProductScore topScore = scores.get(topProduct.getId());
        double overall = topScore != null ? topScore.getOverallScore() : 85.0;

        return String.format(
                "Comparing %d products. %s is rated highest overall (Score: %.1f) with top value across price and rating factors.",
                products.size(), topProduct.getName(), overall
        );
    }

    private List<UUID> parseProductIds(String productIdsStr) {
        if (productIdsStr == null || productIdsStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(productIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
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
