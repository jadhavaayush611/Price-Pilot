package com.pricepilot.product;

import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.product.dto.ProductSearchResultDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@CrossOrigin(origins = "*") // Allows calls from any local frontend client
public class ProductSearchController {

    private final ProductService productService;

    public ProductSearchController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductSearchResultDTO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        
        PageResponse<ProductSearchResultDTO> results = productService.searchProducts(
                keyword, category, brand, page, size, sort);
        return ResponseEntity.ok(results);
    }
}
