package com.pricepilot.intelligence.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantResponseDTO {

    // Primary text response
    private String response;

    // Identifiers
    private UUID conversationId;
    private UUID messageId;
    private UUID activeProductId;

    // Intent & Evidence
    private AssistantIntent intent;
    private AssistantEvidenceBundle evidenceBundle;

    // Backward-compatible fields
    @Builder.Default
    private List<Map<String, Object>> products = new ArrayList<>();

    private Map<String, Object> comparisons;
    private Map<String, Object> buyConfidence;

    @Builder.Default
    private List<String> suggestedPrompts = new ArrayList<>();

    @Builder.Default
    private List<AssistantAction> suggestedActions = new ArrayList<>();
}
