package com.pricepilot.product;

import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.product.dto.ProductSearchResultDTO;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.interaction.InteractionType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@CrossOrigin(origins = "*") // Allows calls from any local frontend client
public class ProductSearchController {

    private final ProductService productService;
    private final UserInteractionEventService eventService;

    public ProductSearchController(ProductService productService, UserInteractionEventService eventService) {
        this.productService = productService;
        this.eventService = eventService;
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

        java.util.Map<String, Object> filters = new java.util.HashMap<>();
        if (category != null && !category.trim().isEmpty()) {
            filters.put("category", category);
        }
        if (brand != null && !brand.trim().isEmpty()) {
            filters.put("brand", brand);
        }

        eventService.trackEvent(
                getAuthenticatedUserEmailOptional(),
                null,
                null,
                InteractionType.SEARCH,
                java.util.Map.of(
                        "keyword", keyword != null ? keyword : "",
                        "filters", filters,
                        "resultCount", results.getTotalElements()
                )
        );

        return ResponseEntity.ok(results);
    }

    private String getAuthenticatedUserEmailOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            String name = principal.toString();
            if ("anonymousUser".equals(name)) {
                return null;
            }
            return name;
        }
    }
}
