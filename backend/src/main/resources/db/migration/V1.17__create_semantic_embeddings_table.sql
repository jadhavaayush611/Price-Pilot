-- Flyway migration to create semantic embeddings table for vector storage
-- Author: Principal Semantic Intelligence Engineer

CREATE TABLE IF NOT EXISTS semantic_embeddings (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    dimension INTEGER NOT NULL,
    vector_data TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_semantic_embedding_entity_model UNIQUE (entity_type, entity_id, model_name, model_version)
);

CREATE INDEX IF NOT EXISTS idx_semantic_embedding_lookup ON semantic_embeddings(entity_type, model_name, model_version);
CREATE INDEX IF NOT EXISTS idx_semantic_embedding_model ON semantic_embeddings(model_name, model_version);
CREATE INDEX IF NOT EXISTS idx_semantic_embedding_updated ON semantic_embeddings(updated_at DESC);
