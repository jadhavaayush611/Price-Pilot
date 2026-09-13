# PricePilot Semantic Intelligence Architecture (v1.2 Phase 1)

This document specifies the architecture, data models, abstractions, and configuration for the **Semantic Intelligence Foundation** in PricePilot v1.2.

---

## 1. Executive Summary & Goals

The PricePilot v1.2 Semantic Intelligence Foundation establishes the core domain abstractions and local infrastructure for vector embedding generation and similarity search across products, categories, and shopping intent.

### Key Architectural Tenets:
1. **Zero External API-Key Dependency**: Built entirely on self-contained, locally runnable infrastructure with zero dependencies on third-party SaaS embedding APIs (OpenAI, Cohere, Anthropic, Pinecone, etc.).
2. **Provider & Storage Portability**: All model interactions and vector persistence operate behind strict Java domain interfaces (`EmbeddingProvider`, `VectorStore`, `EmbeddingService`, `VectorSearchService`), preventing infrastructure coupling.
3. **Model & Version Isolation**: Strict version tagging (`model_name`, `model_version`, `dimension`) ensures vector spaces are never accidentally mixed or compared across model upgrades.
4. **Deterministic Reproducibility**: Identical text inputs always yield identical vector representations and deterministic ranking orders.
5. **PostgreSQL Relational Co-location**: Embeddings are stored transactionally alongside core e-commerce entities in PostgreSQL (and H2 for in-memory testing) without requiring experimental or platform-incompatible C extensions.

---

## 2. Domain Architecture Overview

The semantic intelligence domain is organized under `com.pricepilot.intelligence.semantic`:

```mermaid
graph TD
    subgraph Core Semantic Domain
        ES[EmbeddingService]
        VSS[VectorSearchService]
        SSS[SemanticSearchService]
    end

    subgraph Abstractions
        EP[EmbeddingProvider Interface]
        VS[VectorStore Interface]
    end

    subgraph Implementations
        LDP[LocalDeterministicEmbeddingProvider]
        JVS[JpaVectorStore]
    end

    subgraph Infrastructure
        DB[(PostgreSQL / H2 semantic_embeddings)]
        ACT[Spring Actuator HealthIndicator]
        MIC[Micrometer Metrics Registry]
    end

    ES --> EP
    ES --> MIC
    VSS --> ES
    VSS --> VS
    VSS --> MIC
    SSS --> VSS

    EP <|.. LDP
    VS <|.. JVS

    JVS --> DB
    JVS --> MIC
    ACT --> EP
```

---

## 3. Core Domain Abstractions

### 3.1 `EmbeddingVector`
An immutable, high-dimensional vector value object providing mathematical primitives:
- Cosine similarity: $\text{sim}(\mathbf{u}, \mathbf{v}) = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\| \|\mathbf{v}\|}$
- Dot product and Euclidean distance
- $L_2$ unit-sphere normalization
- String serialization / parsing (`float[]` to comma-delimited string and vice-versa)

### 3.2 `EmbeddingProvider` & `LocalDeterministicEmbeddingProvider`
Defines contracts for converting text to dense vectors:
- `embed(String text)`: Single text embedding
- `embedBatch(List<String> texts)`: Batch text embedding with size constraints
- `checkHealth()`: Returns `ProviderHealth` (UP/DOWN, model name, version, dimension)
- `isAvailable()`: Resilience status check

**Local Implementation Details**:
`LocalDeterministicEmbeddingProvider` tokenizes input into word unigrams, bigrams, and character $n$-grams (3-grams and 4-grams), applying Murmur3-based 64-bit signed feature hashing into a $D$-dimensional bucket space with $L_2$ normalization. This guarantees:
- Fast, non-blocking execution (sub-millisecond latency)
- Identical vector outputs on all JVMs and operating systems
- Semantic morphological sensitivity (similar product names and subwords produce high cosine similarity)

### 3.3 `VectorStore` & `JpaVectorStore`
Defines vector database operations:
- `upsert(EmbeddingRecord record)` & `batchUpsert(List<EmbeddingRecord> records)`
- `similaritySearch(SimilaritySearchRequest request)`: Exact cosine similarity search filtered by model, version, and entity type with deterministic tie-breaking (ordered by score desc, entityId asc)
- `delete(String entityType, String entityId)` & `batchDelete(...)`
- `get(...)`, `exists(...)`, `countByModel(...)`, `deleteAllByModel(...)`

---

## 4. Database Schema (`semantic_embeddings`)

Created via Flyway migration `V1.17__create_semantic_embeddings_table.sql`:

```sql
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
```

---

## 5. Configuration Reference

Configuration is managed via `pricepilot.intelligence.semantic` prefix:

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `pricepilot.intelligence.semantic.enabled` | boolean | `true` | Master toggle for semantic intelligence |
| `pricepilot.intelligence.semantic.provider` | String | `local-deterministic` | Embedding provider bean identifier |
| `pricepilot.intelligence.semantic.model-name` | String | `local-hash-embedding` | Model name identifier |
| `pricepilot.intelligence.semantic.model-version` | String | `v1` | Model version tag for space isolation |
| `pricepilot.intelligence.semantic.dimension` | int | `64` | Embedding vector dimensionality |
| `pricepilot.intelligence.semantic.timeout-ms` | long | `5000` | Max timeout for embedding generation |
| `pricepilot.intelligence.semantic.batch-size` | int | `32` | Max batch size for batch embedding |
| `pricepilot.intelligence.semantic.min-similarity-score` | double | `0.0` | Default minimum similarity threshold |
| `pricepilot.intelligence.semantic.max-input-length` | int | `4096` | Max allowable input character length |

---

## 6. Observability & Health

### Micrometer Metrics

| Metric Name | Type | Description |
| :--- | :--- | :--- |
| `pricepilot.semantic.embedding.count` | Counter | Total embedding generations requested |
| `pricepilot.semantic.embedding.latency` | Timer | Latency distribution of embedding generation |
| `pricepilot.semantic.embedding.failures` | Counter | Total failed embedding operations |
| `pricepilot.semantic.embedding.batch.size` | DistributionSummary | Distribution of batch sizes |
| `pricepilot.semantic.vector.search.count` | Counter | Total vector similarity searches executed |
| `pricepilot.semantic.vector.search.latency` | Timer | Latency distribution of similarity searches |
| `pricepilot.semantic.vector.search.failures` | Counter | Total failed vector searches |
| `pricepilot.semantic.vector.upsert.count` | Counter | Total vector records upserted |

### Actuator Health Indicator
`SemanticIntelligenceHealthIndicator` exposes status at `/actuator/health`:
```json
{
  "status": "UP",
  "details": {
    "provider": "LocalDeterministicEmbeddingProvider",
    "model": "local-hash-embedding",
    "version": "v1",
    "dimension": 64,
    "message": "Provider is operational"
  }
}
```

---

## 7. Security & Safety

- **Untrusted Input Handling**: All text inputs are trimmed, sanitized of control characters, and truncated to `maxInputLength` (4096 characters).
- **No Secret Leakage**: No external API keys or credentials are required or configured.
- **Privacy & Redaction**: Raw text queries are never logged in observability traces; only entity identifiers, model metadata, and latency metrics are recorded.
- **Entity Type & Tenant Isolation**: Similarity searches and storage enforce `entity_type` scoping to prevent cross-domain or cross-user data leakage.

---

## 8. Extension Points for Future Phases

- **Phase 2: Product Embeddings**: Bulk generation and background indexing of product catalog titles, descriptions, and categories into `semantic_embeddings`.
- **Phase 3: Natural Language Search**: Query understanding bridging user search queries to `VectorSearchService` with hybrid lexical-semantic fusion.
- **Phase 4: Semantic Recommendations & Alternatives**: Near-neighbor retrieval for finding direct substitutes and product alternatives.
