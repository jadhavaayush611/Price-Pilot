package com.pricepilot.intelligence.semantic.storage;

import com.pricepilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a stored semantic vector embedding in the relational database.
 */
@Entity
@Table(
        name = "semantic_embeddings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_semantic_embedding_entity_model",
                        columnNames = {"entity_type", "entity_id", "model_name", "model_version"}
                )
        },
        indexes = {
                @Index(name = "idx_semantic_embedding_lookup", columnList = "entity_type, model_name, model_version"),
                @Index(name = "idx_semantic_embedding_model", columnList = "model_name, model_version"),
                @Index(name = "idx_semantic_embedding_updated", columnList = "updated_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticEmbeddingEntity extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "dimension", nullable = false)
    private int dimension;

    @Column(name = "vector_data", nullable = false, columnDefinition = "TEXT")
    private String vectorData;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
