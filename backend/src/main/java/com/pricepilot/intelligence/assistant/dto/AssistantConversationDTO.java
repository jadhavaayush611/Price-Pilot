package com.pricepilot.intelligence.assistant.dto;

import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantConversationDTO {

    private UUID id;
    private UUID userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<AssistantMessageDTO> messages = new ArrayList<>();

    public static AssistantConversationDTO fromEntity(AssistantConversationEntity entity, List<AssistantMessageDTO> messages) {
        if (entity == null) return null;
        return AssistantConversationDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .messages(messages != null ? messages : new ArrayList<>())
                .build();
    }
}
