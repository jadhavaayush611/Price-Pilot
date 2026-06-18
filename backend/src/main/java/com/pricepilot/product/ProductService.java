package com.pricepilot.product;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.dto.ProductRequestDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
        return ProductResponseDTO.fromEntity(entity);
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
}
