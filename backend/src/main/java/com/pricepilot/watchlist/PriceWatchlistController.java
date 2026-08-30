package com.pricepilot.watchlist;

import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.UpdateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
@CrossOrigin(origins = "*")
public class PriceWatchlistController {

    private final PriceWatchlistService watchlistService;
    private final com.pricepilot.intelligence.alert.service.PriceAlertService priceAlertService;
    private final com.pricepilot.user.UserRepository userRepository;

    public PriceWatchlistController(
            PriceWatchlistService watchlistService,
            com.pricepilot.intelligence.alert.service.PriceAlertService priceAlertService,
            com.pricepilot.user.UserRepository userRepository) {
        this.watchlistService = watchlistService;
        this.priceAlertService = priceAlertService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<WatchlistResponseDTO> createWatchlist(@Valid @RequestBody CreateWatchlistRequestDTO requestDTO) {
        String email = getAuthenticatedUserEmail();
        WatchlistResponseDTO response = watchlistService.createWatchlist(email, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WatchlistResponseDTO> updateWatchlist(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWatchlistRequestDTO requestDTO) {
        String email = getAuthenticatedUserEmail();
        WatchlistResponseDTO response = watchlistService.updateWatchlist(email, id, requestDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWatchlist(@PathVariable UUID id) {
        String email = getAuthenticatedUserEmail();
        watchlistService.deleteWatchlist(email, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WatchlistResponseDTO> getWatchlistById(@PathVariable UUID id) {
        String email = getAuthenticatedUserEmail();
        WatchlistResponseDTO response = watchlistService.getWatchlistById(email, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WatchlistResponseDTO>> getAllWatchlists() {
        String email = getAuthenticatedUserEmail();
        List<WatchlistResponseDTO> response = watchlistService.getAllWatchlists(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/alerts")
    public ResponseEntity<com.pricepilot.intelligence.alert.dto.WatchlistAlertPreferenceDTO> getWatchlistAlertPreferences(
            @PathVariable UUID id) {
        UUID userId = getAuthenticatedUserId();
        return ResponseEntity.ok(priceAlertService.getAlertPreferences(id, userId));
    }

    @PutMapping("/{id}/alerts")
    public ResponseEntity<com.pricepilot.intelligence.alert.dto.WatchlistAlertPreferenceDTO> updateWatchlistAlertPreferences(
            @PathVariable UUID id,
            @Valid @RequestBody com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO requestDTO) {
        UUID userId = getAuthenticatedUserId();
        return ResponseEntity.ok(priceAlertService.updateAlertPreferences(id, userId, requestDTO));
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

    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof com.pricepilot.security.UserPrincipal) {
            return ((com.pricepilot.security.UserPrincipal) principal).getId();
        }

        String email = getAuthenticatedUserEmail();
        return userRepository.findByEmail(email)
                .map(com.pricepilot.user.UserEntity::getId)
                .orElseThrow(() -> new com.pricepilot.exception.ResourceNotFoundException("User not found with email: " + email));
    }
}
