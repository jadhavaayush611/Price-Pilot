package com.pricepilot.product;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.exception.InvalidCursorException;
import com.pricepilot.product.dto.ProductRequestDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.product.dto.ProductSearchResultDTO;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import com.pricepilot.product.dto.KeysetPageResponse;
import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.analytics.ProductAnalyticsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductAnalyticsRepository productAnalyticsRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            ProductAnalyticsRepository productAnalyticsRepository) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.productAnalyticsRepository = productAnalyticsRepository;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "product-searches", allEntries = true),
        @CacheEvict(value = "popular-products", allEntries = true)
    })
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        ProductEntity entity = ProductEntity.builder()
                .name(requestDTO.getName())
                .brand(requestDTO.getBrand())
                .category(requestDTO.getCategory())
                .description(requestDTO.getDescription())
                .imageUrl(requestDTO.getImageUrl())
                .archived(requestDTO.isArchived())
                .build();
        
        ProductEntity savedEntity = productRepository.save(entity);

        // Automatically create analytics record when product is created
        ProductAnalyticsEntity analytics = ProductAnalyticsEntity.builder()
                .product(savedEntity)
                .viewCount(0L)
                .saveCount(0L)
                .watchlistCount(0L)
                .priceChangeCount(0L)
                .build();
        productAnalyticsRepository.save(analytics);

        return ProductResponseDTO.fromEntity(savedEntity);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(String search, Pageable pageable) {
        Page<ProductEntity> entityPage;
        if (search != null && !search.trim().isEmpty()) {
            entityPage = productRepository.findByNameContainingIgnoreCaseOrBrandContainingIgnoreCaseOrCategoryContainingIgnoreCase(
                    search, search, search, pageable);
        } else {
            entityPage = productRepository.findAll(pageable);
        }
        return entityPage.map(ProductResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-details", key = "#id")
    public ProductResponseDTO getProductById(UUID id) {
        ProductEntity entity = productRepository.findByIdWithPricesAndSellers(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        ProductResponseDTO dto = ProductResponseDTO.fromEntity(entity);
        if (entity.getProductPrices() != null) {
            dto.setPrices(entity.getProductPrices().stream()
                    .map(priceEntity -> com.pricepilot.productprice.dto.ProductPriceResponseDTO.builder()
                            .id(priceEntity.getId())
                            .currentPrice(priceEntity.getCurrentPrice())
                            .originalPrice(priceEntity.getOriginalPrice())
                            .discountPercentage(priceEntity.getDiscountPercentage())
                            .productUrl(priceEntity.getProductUrl())
                            .lastUpdated(priceEntity.getLastUpdated())
                            .seller(com.pricepilot.seller.dto.SellerResponseDTO.fromEntity(priceEntity.getSeller()))
                            .createdAt(priceEntity.getCreatedAt())
                            .updatedAt(priceEntity.getUpdatedAt())
                            .build()
                    )
                    .collect(java.util.stream.Collectors.toList()));
        }
        return dto;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "product-details", key = "#id"),
        @CacheEvict(value = "product-searches", allEntries = true),
        @CacheEvict(value = "popular-products", allEntries = true)
    })
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO requestDTO) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        entity.setName(requestDTO.getName());
        entity.setBrand(requestDTO.getBrand());
        entity.setCategory(requestDTO.getCategory());
        entity.setDescription(requestDTO.getDescription());
        entity.setImageUrl(requestDTO.getImageUrl());
        entity.setArchived(requestDTO.isArchived());

        ProductEntity updatedEntity = productRepository.save(entity);
        return ProductResponseDTO.fromEntity(updatedEntity);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "product-details", key = "#id"),
        @CacheEvict(value = "product-searches", allEntries = true),
        @CacheEvict(value = "popular-products", allEntries = true)
    })
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-searches", key = "T(java.util.Objects).hash(#keyword, #category, #brand, #page, #size, #sortStr)")
    public PageResponse<ProductSearchResultDTO> searchProducts(
            String keyword,
            String category,
            String brand,
            int page,
            int size,
            String sortStr) {
        
        Sort sort = Sort.unsorted();
        String customSortField = null;
        Sort.Direction customDirection = Sort.Direction.ASC;

        if (sortStr != null && !sortStr.trim().isEmpty() && !sortStr.trim().equalsIgnoreCase("default")) {
            String cleanSort = sortStr.trim().toLowerCase();
            if (cleanSort.equals("price-asc") || cleanSort.equals("price,asc")) {
                customSortField = "price";
                customDirection = Sort.Direction.ASC;
            } else if (cleanSort.equals("price-desc") || cleanSort.equals("price,desc")) {
                customSortField = "price";
                customDirection = Sort.Direction.DESC;
            } else if (cleanSort.equals("discount-desc") || cleanSort.equals("discount,desc")) {
                customSortField = "discount";
                customDirection = Sort.Direction.DESC;
            } else {
                // Standard sorting, e.g., name,asc or createdAt,desc
                String[] parts = sortStr.split(",");
                String property = parts[0].trim();
                
                // Map snake_case to camelCase
                if ("created_at".equalsIgnoreCase(property)) {
                    property = "createdAt";
                } else if ("updated_at".equalsIgnoreCase(property)) {
                    property = "updatedAt";
                } else if ("image_url".equalsIgnoreCase(property)) {
                    property = "imageUrl";
                }

                // Validate property against allowed ProductEntity fields
                java.util.Set<String> validProperties = java.util.Set.of(
                    "id", "name", "brand", "category", "description", "imageUrl", "archived", "createdAt", "updatedAt"
                );
                if (!validProperties.contains(property)) {
                    throw new IllegalArgumentException("Invalid sort property: " + property);
                }

                Sort.Direction direction = Sort.Direction.ASC;
                if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
                    direction = Sort.Direction.DESC;
                }
                sort = Sort.by(direction, property);
            }
        } else if (keyword == null || keyword.trim().isEmpty()) {
            // Default sort by createdAt only if keyword search is NOT active (since keyword search applies FTS ranking order)
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // For custom sorts, the ordering is handled dynamically inside the Specification.
        Pageable pageable = PageRequest.of(page, size, customSortField != null ? Sort.unsorted() : sort);

        Specification<ProductEntity> spec = ProductSpecifications.withFiltersAndCustomSort(
                keyword, category, brand, customSortField, customDirection);

        Page<ProductEntity> productPage = productRepository.findAll(spec, pageable);

        if (productPage.isEmpty()) {
            return PageResponse.from(productPage, java.util.Collections.emptyList());
        }

        // Gather all product IDs to load their associated prices in a single query (prevents N+1)
        List<UUID> productIds = productPage.getContent().stream()
                .map(ProductEntity::getId)
                .collect(Collectors.toList());

        List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);

        // Group prices by product id to map them locally in memory
        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        List<ProductSearchResultDTO> contentList = productPage.getContent().stream().map(product -> {
            List<ProductPriceEntity> productPrices = pricesByProductId.getOrDefault(product.getId(), List.of());
            List<ProductPriceSearchResultDTO> priceDTOs = productPrices.stream()
                    .map(ProductPriceSearchResultDTO::fromEntity)
                    .collect(Collectors.toList());

            BigDecimal lowest = priceDTOs.stream()
                    .map(ProductPriceSearchResultDTO::getCurrentPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(null);

            BigDecimal highest = priceDTOs.stream()
                    .map(ProductPriceSearchResultDTO::getCurrentPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(null);

            ProductSearchResultDTO dto = ProductSearchResultDTO.fromEntity(product);
            dto.setPrices(priceDTOs);
            dto.setLowestPrice(lowest);
            dto.setHighestPrice(highest);
            return dto;
        }).collect(Collectors.toList());

        return PageResponse.from(productPage, contentList);
    }

    @Transactional(readOnly = true)
    public KeysetPageResponse<ProductResponseDTO> getProductsKeyset(String cursor, int limit, String direction) {
        List<ProductEntity> results;
        PageRequest pageRequest = PageRequest.of(0, limit + 1); // request limit + 1 to check hasMore

        if (cursor == null || cursor.trim().isEmpty()) {
            results = productRepository.findFirstPage(pageRequest);
        } else {
            try {
                int underscoreIdx = cursor.lastIndexOf('_');
                if (underscoreIdx == -1) {
                    throw new InvalidCursorException("Invalid cursor format");
                }
                String timeStr = cursor.substring(0, underscoreIdx);
                String uuidStr = cursor.substring(underscoreIdx + 1);
                
                java.time.LocalDateTime createdAt = java.time.LocalDateTime.parse(timeStr);
                UUID id = UUID.fromString(uuidStr);

                if ("prev".equalsIgnoreCase(direction)) {
                    results = productRepository.findPrevPage(createdAt, id, pageRequest);
                } else {
                    results = productRepository.findNextPage(createdAt, id, pageRequest);
                }
            } catch (Exception e) {
                // Fall back to first page on invalid cursor
                results = productRepository.findFirstPage(pageRequest);
            }
        }

        boolean hasMore = results.size() > limit;
        List<ProductEntity> pageContent = hasMore ? new ArrayList<>(results.subList(0, limit)) : new ArrayList<>(results);

        // If we queried 'prev' page, the results are in ASC order (to get closest items),
        // we must reverse them to return them in DESC order (newest first).
        if (cursor != null && "prev".equalsIgnoreCase(direction)) {
            java.util.Collections.reverse(pageContent);
        }

        List<ProductResponseDTO> contentDTOs = pageContent.stream()
                .map(ProductResponseDTO::fromEntity)
                .collect(Collectors.toList());

        String nextCursor = null;
        String prevCursor = null;

        if (!contentDTOs.isEmpty()) {
            // Next cursor is for retrieving older items (further down the list)
            ProductResponseDTO lastItem = contentDTOs.get(contentDTOs.size() - 1);
            nextCursor = lastItem.getCreatedAt().toString() + "_" + lastItem.getId().toString();

            // Prev cursor is for retrieving newer items (further up the list)
            ProductResponseDTO firstItem = contentDTOs.get(0);
            prevCursor = firstItem.getCreatedAt().toString() + "_" + firstItem.getId().toString();
        }

        return KeysetPageResponse.<ProductResponseDTO>builder()
                .content(contentDTOs)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "popular-products")
    public List<ProductResponseDTO> getPopularProducts(int limit) {
        List<ProductEntity> entities = productRepository.findPopularProducts(PageRequest.of(0, limit));
        return entities.stream()
                .map(ProductResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "trending-products", key = "#limit")
    public List<ProductResponseDTO> getTrendingProducts(int limit) {
        List<ProductEntity> entities = productRepository.findTrendingProducts(PageRequest.of(0, limit));
        return mapProductsToResponseDTOs(entities);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "most-watched-products", key = "#limit")
    public List<ProductResponseDTO> getMostWatchedProducts(int limit) {
        List<ProductEntity> entities = productRepository.findMostWatchedProducts(PageRequest.of(0, limit));
        return mapProductsToResponseDTOs(entities);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "most-saved-products", key = "#limit")
    public List<ProductResponseDTO> getMostSavedProducts(int limit) {
        List<ProductEntity> entities = productRepository.findMostSavedProducts(PageRequest.of(0, limit));
        return mapProductsToResponseDTOs(entities);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "biggest-drops", key = "#limit")
    public List<ProductResponseDTO> getProductsWithBiggestDrops(int limit) {
        List<ProductEntity> entities = productRepository.findProductsWithBiggestDrops(PageRequest.of(0, limit));
        return mapProductsToResponseDTOs(entities);
    }

    private List<ProductResponseDTO> mapProductsToResponseDTOs(List<ProductEntity> products) {
        if (products.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<UUID> productIds = products.stream()
                .map(ProductEntity::getId)
                .collect(Collectors.toList());

        List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);

        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        return products.stream().map(product -> {
            ProductResponseDTO dto = ProductResponseDTO.fromEntity(product);
            List<ProductPriceEntity> productPrices = pricesByProductId.getOrDefault(product.getId(), List.of());
            dto.setPrices(productPrices.stream()
                    .map(priceEntity -> com.pricepilot.productprice.dto.ProductPriceResponseDTO.builder()
                            .id(priceEntity.getId())
                            .currentPrice(priceEntity.getCurrentPrice())
                            .originalPrice(priceEntity.getOriginalPrice())
                            .discountPercentage(priceEntity.getDiscountPercentage())
                            .productUrl(priceEntity.getProductUrl())
                            .lastUpdated(priceEntity.getLastUpdated())
                            .seller(com.pricepilot.seller.dto.SellerResponseDTO.fromEntity(priceEntity.getSeller()))
                            .createdAt(priceEntity.getCreatedAt())
                            .updatedAt(priceEntity.getUpdatedAt())
                            .build()
                    )
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }
}
