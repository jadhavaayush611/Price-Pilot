package com.pricepilot.product;

import com.pricepilot.product.dto.ProductRequestDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.analytics.ProductAnalyticsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "*") // Allows calls from any local frontend client
public class ProductController {

    private final ProductService productService;
    private final ProductAnalyticsService productAnalyticsService;

    public ProductController(ProductService productService, ProductAnalyticsService productAnalyticsService) {
        this.productService = productService;
        this.productAnalyticsService = productAnalyticsService;
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO) {
        ProductResponseDTO createdProduct = productService.createProduct(requestDTO);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductResponseDTO> products = productService.getAllProducts(search, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/popular")
    public ResponseEntity<List<ProductResponseDTO>> getPopularProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> popular = productService.getPopularProducts(limit);
        return ResponseEntity.ok(popular);
    }

    @GetMapping("/keyset")
    public ResponseEntity<com.pricepilot.product.dto.KeysetPageResponse<ProductResponseDTO>> getProductsKeyset(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "next") String direction) {
        com.pricepilot.product.dto.KeysetPageResponse<ProductResponseDTO> response = 
                productService.getProductsKeyset(cursor, limit, direction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trending")
    public ResponseEntity<List<ProductResponseDTO>> getTrendingProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> trending = productService.getTrendingProducts(limit);
        return ResponseEntity.ok(trending);
    }

    @GetMapping("/biggest-drops")
    public ResponseEntity<List<ProductResponseDTO>> getProductsWithBiggestDrops(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> drops = productService.getProductsWithBiggestDrops(limit);
        return ResponseEntity.ok(drops);
    }

    @GetMapping("/most-watched")
    public ResponseEntity<List<ProductResponseDTO>> getMostWatchedProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> mostWatched = productService.getMostWatchedProducts(limit);
        return ResponseEntity.ok(mostWatched);
    }

    @GetMapping("/most-saved")
    public ResponseEntity<List<ProductResponseDTO>> getMostSavedProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductResponseDTO> mostSaved = productService.getMostSavedProducts(limit);
        return ResponseEntity.ok(mostSaved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable UUID id) {
        ProductResponseDTO product = productService.getProductById(id);
        productAnalyticsService.trackView(id);
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequestDTO requestDTO) {
        ProductResponseDTO updatedProduct = productService.updateProduct(id, requestDTO);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
