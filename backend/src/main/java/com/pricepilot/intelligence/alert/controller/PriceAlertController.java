package com.pricepilot.intelligence.alert.controller;

import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public ResponseEntity<Page<PriceAlertResponseDTO>> getUserAlerts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        validateAuthenticated(principal);
        return ResponseEntity.ok(priceAlertService.getUserAlerts(principal.getId(), pageable));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<PriceAlertResponseDTO>> getUnreadAlerts(
            @AuthenticationPrincipal UserPrincipal principal) {
        validateAuthenticated(principal);
        return ResponseEntity.ok(priceAlertService.getUnreadAlerts(principal.getId()));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        validateAuthenticated(principal);
        long count = priceAlertService.getUnreadCount(principal.getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<PriceAlertResponseDTO> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        validateAuthenticated(principal);
        return ResponseEntity.ok(priceAlertService.markAsRead(id, principal.getId()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        validateAuthenticated(principal);
        int updated = priceAlertService.markAllAsRead(principal.getId());
        return ResponseEntity.ok(Map.of("markedCount", updated));
    }

    private void validateAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required to access price alerts");
        }
    }
}
