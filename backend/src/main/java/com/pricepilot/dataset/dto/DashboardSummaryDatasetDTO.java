package com.pricepilot.dataset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDatasetDTO {
    private UUID userId;
    private String email;
    private String role;
    private long savedCount;
    private long watchlistCount;
    private long totalActivitiesCount;
    private long activePriceAlertsCount;
    private LocalDateTime createdAt;
}
