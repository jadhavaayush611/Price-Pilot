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
public class SearchHistoryDatasetDTO {
    private UUID id;
    private UUID userId;
    private String keyword;
    private LocalDateTime createdAt;
}
