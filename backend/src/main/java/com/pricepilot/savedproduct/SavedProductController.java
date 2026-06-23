package com.pricepilot.savedproduct;

import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/saved-products")
@CrossOrigin(origins = "*")
public class SavedProductController {

    private final SavedProductService savedProductService;

    public SavedProductController(SavedProductService savedProductService) {
        this.savedProductService = savedProductService;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> saveProduct(@PathVariable UUID productId) {
        String email = getAuthenticatedUserEmail();
        savedProductService.saveProduct(email, productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeProduct(@PathVariable UUID productId) {
        String email = getAuthenticatedUserEmail();
        savedProductService.removeProduct(email, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<SavedProductResponseDTO>> getSavedProducts() {
        String email = getAuthenticatedUserEmail();
        List<SavedProductResponseDTO> savedProducts = savedProductService.getSavedProducts(email);
        return ResponseEntity.ok(savedProducts);
    }

    private String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            return principal.toString();
        }
    }
}
