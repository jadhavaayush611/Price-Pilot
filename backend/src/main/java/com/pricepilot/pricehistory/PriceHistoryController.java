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

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    public PriceHistoryController(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
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
}
