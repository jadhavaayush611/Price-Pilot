package com.pricepilot.pricehistory;

import com.pricepilot.pricehistory.dto.PriceHistoryResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.interaction.InteractionType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;
    private final UserInteractionEventService eventService;

    public PriceHistoryController(PriceHistoryService priceHistoryService, UserInteractionEventService eventService) {
        this.priceHistoryService = priceHistoryService;
        this.eventService = eventService;
    }

    @GetMapping("/price-history")
    public ResponseEntity<Page<PriceHistoryResponseDTO>> getAllPriceHistory(
            @PageableDefault(size = 10, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PriceHistoryResponseDTO> history = priceHistoryService.getAllPriceHistory(pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/products/{productId}/price-history")
    public ResponseEntity<Page<PriceHistoryResponseDTO>> getPriceHistoryByProduct(
            @PathVariable UUID productId,
            @PageableDefault(size = 10, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PriceHistoryResponseDTO> history = priceHistoryService.getPriceHistoryByProduct(productId, pageable);

        eventService.trackEvent(
                getAuthenticatedUserEmailOptional(),
                productId,
                null,
                InteractionType.PRICE_HISTORY_VIEW,
                java.util.Map.of(
                        "productId", productId.toString(),
                        "resultCount", history.getTotalElements()
                )
        );

        return ResponseEntity.ok(history);
    }

    @GetMapping("/sellers/{sellerId}/price-history")
    public ResponseEntity<Page<PriceHistoryResponseDTO>> getPriceHistoryBySeller(
            @PathVariable UUID sellerId,
            @PageableDefault(size = 10, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PriceHistoryResponseDTO> history = priceHistoryService.getPriceHistoryBySeller(sellerId, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/price-history/largest-drops")
    public ResponseEntity<List<PriceHistoryResponseDTO>> getLargestPriceDrops(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(priceHistoryService.getLargestPriceDrops(limit));
    }

    @GetMapping("/price-history/largest-increases")
    public ResponseEntity<List<PriceHistoryResponseDTO>> getLargestPriceIncreases(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(priceHistoryService.getLargestPriceIncreases(limit));
    }

    @GetMapping("/price-history/recent")
    public ResponseEntity<List<PriceHistoryResponseDTO>> getRecentPriceChanges(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(priceHistoryService.getRecentPriceChanges(limit));
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
