package com.pricepilot.recommendation;

import com.pricepilot.ai.RecommendationService;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ProductService productService;
    private final UserRepository userRepository;

    public RecommendationController(
            RecommendationService recommendationService,
            ProductService productService,
            UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.productService = productService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponseDTO>> getRecommendations(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String email = getAuthenticatedUserEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        PageResponse<ProductResponseDTO> response = recommendationService.getPersonalizedRecommendations(
                user.getId(), category, brand, minPrice, maxPrice, sort, page, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/similar/{productId}")
    public ResponseEntity<List<ProductResponseDTO>> getSimilarProducts(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<ProductResponseDTO> similar = recommendationService.getSimilarProducts(productId, limit);
        return ResponseEntity.ok(similar);
    }

    @GetMapping("/trending")
    public ResponseEntity<List<ProductResponseDTO>> getTrendingProducts(
            @RequestParam(defaultValue = "10") int limit) {
        
        List<ProductResponseDTO> trending = productService.getTrendingProducts(limit);
        return ResponseEntity.ok(trending);
    }

    private String getAuthenticatedUserEmail() {
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
