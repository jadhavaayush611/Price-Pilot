package com.pricepilot.product;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.dto.ProductRequestDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.product.dto.ProductSearchResultDTO;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;

    public ProductService(ProductRepository productRepository, ProductPriceRepository productPriceRepository) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        ProductEntity entity = ProductEntity.builder()
                .name(requestDTO.getName())
                .brand(requestDTO.getBrand())
                .category(requestDTO.getCategory())
                .description(requestDTO.getDescription())
                .imageUrl(requestDTO.getImageUrl())
                .build();
        
        ProductEntity savedEntity = productRepository.save(entity);
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
    public ProductResponseDTO getProductById(UUID id) {
        ProductEntity entity = productRepository.findById(id)
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
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO requestDTO) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        entity.setName(requestDTO.getName());
        entity.setBrand(requestDTO.getBrand());
        entity.setCategory(requestDTO.getCategory());
        entity.setDescription(requestDTO.getDescription());
        entity.setImageUrl(requestDTO.getImageUrl());

        ProductEntity updatedEntity = productRepository.save(entity);
        return ProductResponseDTO.fromEntity(updatedEntity);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<ProductSearchResultDTO> searchProducts(
            String keyword,
            String category,
            String brand,
            int page,
            int size,
            String sortStr) {
        
        Sort sort = Sort.unsorted();
        String customSortField = null;
        Sort.Direction customDirection = Sort.Direction.ASC;

        if (sortStr != null && !sortStr.trim().isEmpty()) {
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
                Sort.Direction direction = Sort.Direction.ASC;
                if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
                    direction = Sort.Direction.DESC;
                }
                sort = Sort.by(direction, property);
            }
        } else {
            // Default sort
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // For custom sorts, the ordering is handled dynamically inside the Specification.
        Pageable pageable = PageRequest.of(page, size, customSortField != null ? Sort.unsorted() : sort);

        Specification<ProductEntity> spec = ProductSpecifications.withFiltersAndCustomSort(
                keyword, category, brand, customSortField, customDirection);

        Page<ProductEntity> productPage = productRepository.findAll(spec, pageable);

        if (productPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // Gather all product IDs to load their associated prices in a single query (prevents N+1)
        List<UUID> productIds = productPage.getContent().stream()
                .map(ProductEntity::getId)
                .collect(Collectors.toList());

        List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);

        // Group prices by product id to map them locally in memory
        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        return productPage.map(product -> {
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
        });
    }
}
