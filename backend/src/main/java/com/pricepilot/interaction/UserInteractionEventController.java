package com.pricepilot.interaction;

import com.pricepilot.interaction.dto.UserInteractionEventResponseDTO;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@CrossOrigin(origins = "*")
public class UserInteractionEventController {

    private final UserInteractionEventService eventService;
    private final UserRepository userRepository;
    private final com.pricepilot.productprice.ProductPriceRepository productPriceRepository;

    public UserInteractionEventController(
            UserInteractionEventService eventService,
            UserRepository userRepository,
            com.pricepilot.productprice.ProductPriceRepository productPriceRepository) {
        this.eventService = eventService;
        this.userRepository = userRepository;
        this.productPriceRepository = productPriceRepository;
    }

    @PostMapping("/seller-click/{priceId}")
    public ResponseEntity<Void> trackSellerClick(@PathVariable UUID priceId) {
        String email = getAuthenticatedUserEmail();
        com.pricepilot.productprice.ProductPriceEntity price = productPriceRepository.findByIdWithRelations(priceId)
                .orElseThrow(() -> new com.pricepilot.exception.ResourceNotFoundException("Product price not found: " + priceId));

        java.util.Map<String, Object> metadata = java.util.Map.of(
                "seller", price.getSeller().getName(),
                "product", price.getProduct().getName(),
                "destinationUrl", price.getProductUrl() != null ? price.getProductUrl() : "",
                "timestamp", java.time.LocalDateTime.now().toString()
        );

        eventService.trackEvent(
                email,
                price.getProduct().getId(),
                price.getSeller().getId(),
                InteractionType.SELLER_CLICK,
                metadata
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<UserInteractionEventResponseDTO>> getEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<UserInteractionEventResponseDTO> events = eventService.getEvents(
                userId, productId, sellerId, type, startDate, endDate, search, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<UserInteractionEventResponseDTO>> getMyEvents(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        String email = getAuthenticatedUserEmail();
        if (email == null) {
            throw new BadCredentialsException("User not authenticated");
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + email));

        Page<UserInteractionEventResponseDTO> events = eventService.getEvents(
                user.getId(), productId, sellerId, type, startDate, endDate, search, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<Page<UserInteractionEventResponseDTO>> getEventsByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<UserInteractionEventResponseDTO> events = eventService.getEvents(
                userId, productId, sellerId, type, startDate, endDate, search, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserInteractionEventResponseDTO>> searchEvents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<UserInteractionEventResponseDTO> events = eventService.getEvents(
                null, null, null, type, startDate, endDate, search, pageable);
        return ResponseEntity.ok(events);
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
            // In some custom security setups, principal is a string representing email
            String name = principal.toString();
            if ("anonymousUser".equals(name)) {
                return null;
            }
            return name;
        }
    }
}
