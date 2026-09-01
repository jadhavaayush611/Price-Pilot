package com.pricepilot.intelligence.assistant.model;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assistant_messages", indexes = {
    @Index(name = "idx_assistant_msg_conv_created", columnList = "conversation_id, created_at ASC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AssistantConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AssistantIntent intent;

    @Column(name = "evidence_bundle", columnDefinition = "TEXT")
    private String evidenceBundle;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
