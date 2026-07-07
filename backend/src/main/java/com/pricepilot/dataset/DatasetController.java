package com.pricepilot.dataset;

import com.pricepilot.dataset.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping("/products")
    public ResponseEntity<?> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<ProductDatasetDTO> dataset = datasetService.getProductsDataset(category, brand, archived, startDate, endDate, pageable);
        return handleExport(dataset, ProductDatasetDTO.class, "products", format);
    }

    @GetMapping("/product-analytics")
    public ResponseEntity<?> getProductAnalytics(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<ProductAnalyticsDatasetDTO> dataset = datasetService.getProductAnalyticsDataset(productId, startDate, endDate, pageable);
        return handleExport(dataset, ProductAnalyticsDatasetDTO.class, "product-analytics", format);
    }

    @GetMapping("/interaction-events")
    public ResponseEntity<?> getInteractionEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<InteractionEventDatasetDTO> dataset = datasetService.getInteractionEventsDataset(userId, productId, sellerId, type, startDate, endDate, pageable);
        return handleExport(dataset, InteractionEventDatasetDTO.class, "interaction-events", format);
    }

    @GetMapping("/watchlists")
    public ResponseEntity<?> getWatchlists(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<WatchlistDatasetDTO> dataset = datasetService.getWatchlistsDataset(userId, productId, active, startDate, endDate, pageable);
        return handleExport(dataset, WatchlistDatasetDTO.class, "watchlists", format);
    }

    @GetMapping("/saved-products")
    public ResponseEntity<?> getSavedProducts(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<SavedProductDatasetDTO> dataset = datasetService.getSavedProductsDataset(userId, productId, startDate, endDate, pageable);
        return handleExport(dataset, SavedProductDatasetDTO.class, "saved-products", format);
    }

    @GetMapping("/search-history")
    public ResponseEntity<?> getSearchHistory(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<SearchHistoryDatasetDTO> dataset = datasetService.getSearchHistoryDataset(userId, keyword, startDate, endDate, pageable);
        return handleExport(dataset, SearchHistoryDatasetDTO.class, "search-history", format);
    }

    @GetMapping("/dashboard-summary")
    public ResponseEntity<?> getDashboardSummary(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<DashboardSummaryDatasetDTO> dataset = datasetService.getDashboardSummaryDataset(userId, role, startDate, endDate, pageable);
        return handleExport(dataset, DashboardSummaryDatasetDTO.class, "dashboard-summary", format);
    }

    @GetMapping("/price-history")
    public ResponseEntity<?> getPriceHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "json") String format,
            Pageable pageable) {

        Page<PriceHistoryDatasetDTO> dataset = datasetService.getPriceHistoryDataset(productId, sellerId, startDate, endDate, pageable);
        return handleExport(dataset, PriceHistoryDatasetDTO.class, "price-history", format);
    }

    private <T> ResponseEntity<?> handleExport(Page<T> page, Class<T> clazz, String filename, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            String csvContent = CsvConverter.toCsv(page.getContent(), clazz);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csvContent);
        }
        return ResponseEntity.ok(page);
    }
}
