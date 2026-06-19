package com.pricepilot.productprice;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.productprice.dto.ProductPriceRequestDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductPriceService {

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    public ProductPriceService(ProductPriceRepository productPriceRepository,
                               ProductRepository productRepository,
                               SellerRepository sellerRepository) {
        this.productPriceRepository = productPriceRepository;
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public ProductPriceResponseDTO createProductPrice(ProductPriceRequestDTO requestDTO) {
        ProductEntity product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDTO.getProductId()));

        SellerEntity seller = sellerRepository.findById(requestDTO.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + requestDTO.getSellerId()));

        // Validate prices before creating (extra check in Service layer)
        validatePrices(requestDTO);

        ProductPriceEntity entity = ProductPriceEntity.builder()
                .product(product)
                .seller(seller)
                .currentPrice(requestDTO.getCurrentPrice())
                .originalPrice(requestDTO.getOriginalPrice())
                .productUrl(requestDTO.getProductUrl())
                .build();

        ProductPriceEntity savedEntity = productPriceRepository.save(entity);
        return ProductPriceResponseDTO.fromEntity(savedEntity);
    }

    @Transactional(readOnly = true)
    public Page<ProductPriceResponseDTO> getAllProductPrices(String search, Pageable pageable) {
        return productPriceRepository.findAllWithRelations(search, pageable)
                .map(ProductPriceResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProductPriceResponseDTO getProductPriceById(UUID id) {
        ProductPriceEntity entity = productPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product price not found with id: " + id));
        return ProductPriceResponseDTO.fromEntity(entity);
    }

    @Transactional
    public ProductPriceResponseDTO updateProductPrice(UUID id, ProductPriceRequestDTO requestDTO) {
        ProductPriceEntity entity = productPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product price not found with id: " + id));

        ProductEntity product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDTO.getProductId()));

        SellerEntity seller = sellerRepository.findById(requestDTO.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + requestDTO.getSellerId()));

        // Validate prices before updating
        validatePrices(requestDTO);

        entity.setProduct(product);
        entity.setSeller(seller);
        entity.setCurrentPrice(requestDTO.getCurrentPrice());
        entity.setOriginalPrice(requestDTO.getOriginalPrice());
        entity.setProductUrl(requestDTO.getProductUrl());

        ProductPriceEntity updatedEntity = productPriceRepository.save(entity);
        return ProductPriceResponseDTO.fromEntity(updatedEntity);
    }

    @Transactional
    public void deleteProductPrice(UUID id) {
        if (!productPriceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product price not found with id: " + id);
        }
        productPriceRepository.deleteById(id);
    }

    private void validatePrices(ProductPriceRequestDTO requestDTO) {
        if (requestDTO.getOriginalPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Original price must be greater than zero");
        }
        if (requestDTO.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current price cannot be negative");
        }
        if (requestDTO.getCurrentPrice().compareTo(requestDTO.getOriginalPrice()) > 0) {
            throw new IllegalArgumentException("Current price cannot be greater than original price (invalid discount)");
        }
    }
}
