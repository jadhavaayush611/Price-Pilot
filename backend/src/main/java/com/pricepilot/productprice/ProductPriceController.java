package com.pricepilot.productprice;

import com.pricepilot.productprice.dto.ProductPriceRequestDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prices")
@CrossOrigin(origins = "*")
public class ProductPriceController {

    private final ProductPriceService productPriceService;

    public ProductPriceController(ProductPriceService productPriceService) {
        this.productPriceService = productPriceService;
    }

    @PostMapping
    public ResponseEntity<ProductPriceResponseDTO> createProductPrice(@Valid @RequestBody ProductPriceRequestDTO requestDTO) {
        ProductPriceResponseDTO createdPrice = productPriceService.createProductPrice(requestDTO);
        return new ResponseEntity<>(createdPrice, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<ProductPriceResponseDTO>> getAllProductPrices(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "lastUpdated", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductPriceResponseDTO> prices = productPriceService.getAllProductPrices(search, pageable);
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductPriceResponseDTO> getProductPriceById(@PathVariable UUID id) {
        ProductPriceResponseDTO price = productPriceService.getProductPriceById(id);
        return ResponseEntity.ok(price);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductPriceResponseDTO> updateProductPrice(
            @PathVariable UUID id,
            @Valid @RequestBody ProductPriceRequestDTO requestDTO) {
        ProductPriceResponseDTO updatedPrice = productPriceService.updateProductPrice(id, requestDTO);
        return ResponseEntity.ok(updatedPrice);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductPrice(@PathVariable UUID id) {
        productPriceService.deleteProductPrice(id);
        return ResponseEntity.noContent().build();
    }
}
