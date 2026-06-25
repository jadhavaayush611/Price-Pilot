package com.pricepilot.interaction;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_interaction_events", indexes = {
    @Index(name = "idx_user_interaction_events_user_id", columnList = "user_id"),
    @Index(name = "idx_user_interaction_events_product_id", columnList = "product_id"),
    @Index(name = "idx_user_interaction_events_seller_id", columnList = "seller_id"),
    @Index(name = "idx_user_interaction_events_type_created", columnList = "interaction_type, created_at"),
    @Index(name = "idx_user_interaction_events_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserInteractionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", updatable = false)
    private SellerEntity seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, updatable = false)
    private InteractionType interactionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false, updatable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
