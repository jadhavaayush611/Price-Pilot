package com.pricepilot.intelligence.assistant.dto;

import com.pricepilot.intelligence.assistant.model.AssistantMessageEntity;
import com.pricepilot.intelligence.assistant.model.MessageRole;
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
public class AssistantMessageDTO {

    private UUID id;
    private UUID conversationId;
    private MessageRole role;
    private String content;
    private AssistantIntent intent;
    private AssistantEvidenceBundle evidenceBundle;
    private Object payload;
    private LocalDateTime createdAt;

    public static AssistantMessageDTO fromEntity(AssistantMessageEntity entity, AssistantEvidenceBundle bundle, Object payload) {
        if (entity == null) return null;
        return AssistantMessageDTO.builder()
                .id(entity.getId())
                .conversationId(entity.getConversation() != null ? entity.getConversation().getId() : null)
                .role(entity.getRole())
                .content(entity.getContent())
                .intent(entity.getIntent())
                .evidenceBundle(bundle)
                .payload(payload)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
