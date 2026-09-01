package com.pricepilot.intelligence.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantAction {
    private String type; // COMPARE, VIEW_ANALYTICS, ADD_WATCHLIST, SET_TARGET_PRICE, EXPLORE_CATEGORY, UPDATE_PREFERENCES
    private String label;
    private String description;
    private Map<String, Object> payload;
}
