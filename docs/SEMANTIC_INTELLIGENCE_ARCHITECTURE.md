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

## 8. Phase 2: Product Embedding Pipeline & Canonical Product Text Contract

### 8.1 Canonical Product Text Contract (`product-semantic-v1`)

The **Canonical Product Text Contract** establishes a formal, deterministic translation of e-commerce `ProductEntity` records into standardized semantic text representations prior to vector embedding.

#### Field Inclusion & Exclusion Rationale

| Field Name | Domain Type | Inclusion Decision | Rationale |
| :--- | :--- | :---: | :--- |
| `name` | Core Identity | **INCLUDED** (Prefix: `title:`) | Primary semantic product identifier and user-facing title. |
| `brand` | Core Identity | **INCLUDED** (Prefix: `brand:`) | Manufacturer / brand identity; omitted if null or blank. |
| `category` | Taxonomy | **INCLUDED** (Prefix: `category:`) | Domain category taxonomy defining semantic product class. |
| `description` | Semantic Details | **INCLUDED** (Prefix: `description:`) | Technical specifications and features; truncated if > `maxDescriptionLength`. |
| `currentPrice`, `originalPrice` | Shopping Intel | **EXCLUDED** | Highly volatile; changing prices must not dirty the semantic vector space. |
| `discountPercentage` | Shopping Intel | **EXCLUDED** | Promotional volatility; belongs to deterministic ranking filters. |
| `imageUrl`, `productUrl` | Media / Links | **EXCLUDED** | Non-semantic navigational and asset identifiers. |
| `seller`, `sellerId` | Merchant Intel | **EXCLUDED** | Multi-seller pricing variants should not distort the core product identity. |
| `analytics` (views, saves, etc.) | Behavioral Intel | **EXCLUDED** | Dynamic real-time counters belonging to discovery ranking layers. |
| `priceHistories` | Historical Intel | **EXCLUDED** | Time-series data irrelevant to intrinsic semantic identity. |
| `searchVector` | Internal Postgres | **EXCLUDED** | Database-internal PostgreSQL FTS tsvector column. |

#### Canonical Normalization Pipeline
1. **Unicode Normalization:** Normalized via `java.text.Normalizer.normalize(text, Normalizer.Form.NFC)` (canonical composition).
2. **Control Character Stripping:** `[\p{Cntrl}&&[^\r\n\t]]` stripped to prevent serialization anomalies.
3. **Whitespace Collapsing:** Consecutive whitespace runs (`\s+`, tabs, newlines) collapsed into single ASCII spaces (` `), trimmed at boundaries.
4. **Structured Format:** Formatted as `title: <title> | brand: <brand> | category: <category> | description: <description>`.
5. **Deterministic Hashing:** Computes SHA-256 hash stored in `metadata_json.semanticHash` for zero-overhead change detection during incremental updates.

### 8.2 Product Embedding Pipeline Architecture

```mermaid
flowchart TD
    subgraph LifecycleEvents ["Product Lifecycle Events"]
        CE[ProductCreatedEvent]
        UE[ProductUpdatedEvent]
        DE[ProductDeletedEvent]
    end

    subgraph Pipeline ["Product Embedding Pipeline"]
        LISTENER[ProductSemanticEventListener]
        PES[ProductEmbeddingService]
        PBIS[ProductBatchIndexingService]
        CIS[CatalogIndexingService]
        BUILDER[CanonicalProductTextBuilder]
    end

    subgraph CoreSemantic ["Core Semantic Foundation"]
        ES[EmbeddingService]
        EP[EmbeddingProvider]
        VS[VectorStore]
        DB[(semantic_embeddings)]
    end

    CE --> LISTENER
    UE --> LISTENER
    DE --> LISTENER

    LISTENER -->|Has Semantic Diff| PES
    CIS --> PBIS
    PBIS --> PES

    PES --> BUILDER
    BUILDER -->|Canonical Text| PES
    PES --> ES
    ES --> EP
    PES --> VS
    VS --> DB
```

### 8.3 Incremental & Batch Indexing Features
- **Incremental Event-Driven Indexing:** `ProductSemanticEventListener` listens to lifecycle events. If a product update only alters volatile fields (price, seller, discount, image), semantic re-embedding is skipped.
- **Chunked Batch Processing:** `ProductBatchIndexingService` executes bounded pagination via `ProductRepository.findByArchivedFalse(PageRequest.of(page, chunkSize, Sort.by("id")))`, preventing heap exhaustion on large catalogs.
- **Partial Failure Isolation:** Batch indexing isolates individual product errors; a single malformed product does not fail the entire chunk.
- **Archival Synchronization:** Archived products are excluded from indexing, and existing embeddings are automatically pruned from the vector store upon archival.

---

## 9. Phase 3: Hybrid Search & Candidate Fusion Architecture

### 9.1 Core Architectural Philosophy
> **"Semantic retrieval is for candidate discovery. Deterministic PricePilot intelligence remains the source of truth for final ranking, filtering, and price analytics."**

Hybrid search combines the high precision of lexical/keyword filtering with the high recall and conceptual matching of semantic vector similarity.

### 9.2 Candidate Fusion & Lifecycle Pipeline

```mermaid
flowchart TD
    subgraph Request ["1. User Search Request"]
        REQ["/api/v1/discovery/hybrid<br/>or /api/v1/discovery/products?hybrid=true"]
    end

    subgraph DualRetrieval ["2. Dual Candidate Retrieval"]
        QI[QueryInterpreter]
        SPEC["Structured Retrieval<br/>(JPA Specification)"]
        SEM["Semantic Vector Retrieval<br/>(Local VectorStore)"]
    end

    subgraph Resilience ["3. Resilience & Hard Filter Pre-Validation"]
        GD["Graceful Degradation Fallback<br/>(Fallback to Structured-Only on Error)"]
        VAL["Hard Taxonomy & Archival Validation"]
    end

    subgraph FusionEngine ["4. Candidate Set Fusion"]
        RRF["Reciprocal Rank Fusion (RRF)<br/>score = Σ (w_i / (k + rank_i))<br/>Deterministic Tie-Breaking"]
    end

    subgraph Verification ["5. Authoritative Hard Filter & Price Enforcement"]
        HF["Hard Constraints Enforcement<br/>• Min/Max Price<br/>• In-Stock Status<br/>• Seller / Merchant ID<br/>• Minimum Discount %"]
    end

    subgraph Intelligence ["6. Deterministic Intelligence & Enrichment"]
        REL["DefaultSearchRelevanceScorer<br/>(Lexical + Semantic Provenance Signals)"]
        PA["PriceAnalyticsService<br/>(DealQuality, PriceTrends, HistoricalLow)"]
    end

    subgraph Response ["7. Response Formation"]
        RESP["DiscoverySearchResponseDTO<br/>(Paged Slice, Provenance Badges, Facets)"]
    end

    REQ --> QI
    QI --> SPEC
    QI --> SEM
    SEM -.->|On Failure| GD
    GD --> RRF
    SEM --> VAL
    VAL --> RRF
    SPEC --> RRF
    RRF --> HF
    HF --> REL
    REL --> PA
    PA --> RESP
```

### 9.3 Key Components in `com.pricepilot.intelligence.discovery.hybrid`

1. **`HybridSearchService` & `HybridSearchServiceImpl`**:
   - Manages the dual-candidate orchestration pipeline with bounded query limits (`structuredCandidateLimit`, `semanticCandidateLimit`, `maxFusedCandidates`).
   - Implements graceful degradation: any semantic model/vector store failure immediately falls back to structured results with metric tracking (`pricepilot.hybrid.search.degraded`).
   - Enforces hard constraints: guarantees that semantic candidates violating price ranges, stock requirements, or merchant filters are strictly excluded.
   - Enriches candidate items with provenance badges (`Semantic Discovery`, `Semantic Match`) and explanation strings.

2. **`CandidateProvenance`**:
   - Enum indicating the retrieval source: `STRUCTURED` (keyword only), `SEMANTIC` (vector search only), or `BOTH` (dual confirmation).

3. **`HybridCandidateFusionStrategy` & `ReciprocalRankFusionStrategy`**:
   - Implements Reciprocal Rank Fusion (RRF) with default smoothing constant $k = 60.0$:
     $$RRF(d) = w_{\text{struct}} \cdot \frac{1}{k + r_{\text{struct}}(d)} + w_{\text{sem}} \cdot \frac{1}{k + r_{\text{sem}}(d)}$$
   - Provides deterministic tie-breaking on identical scores (ordered by `score DESC`, `productId ASC`).

4. **REST API Endpoint (`DiscoveryController`)**:
   - Dedicated endpoint: `GET /api/v1/discovery/hybrid` with parameters for query, category, brand, minPrice, maxPrice, inStock, sellerId, minDiscount, page, size, sort, structuredWeight, semanticWeight.
   - Backward-compatible enhancement: `GET /api/v1/discovery/products?hybrid=true` allows seamless hybrid retrieval through the existing discovery endpoint.

### 9.4 Hybrid Observability & Metrics

| Metric Name | Type | Description |
| :--- | :--- | :--- |
| `pricepilot.hybrid.search.requests` | Counter | Total hybrid search requests initiated |
| `pricepilot.hybrid.search.failures` | Counter | Total failed hybrid search executions |
| `pricepilot.hybrid.search.degraded` | Counter | Number of times search gracefully degraded to structured-only |
| `pricepilot.hybrid.search.duration` | Timer | Latency distribution of hybrid search operations |
| `pricepilot.hybrid.search.structured.candidates` | DistributionSummary | Size distribution of structured candidate pools |
| `pricepilot.hybrid.search.semantic.candidates` | DistributionSummary | Size distribution of semantic candidate pools |
| `pricepilot.hybrid.search.fused.candidates` | DistributionSummary | Size distribution of fused candidate pools |

---

## 10. Extension Points for Future Phases

- **Phase 4: Natural Language Intent Parsing & Extraction**: Conversational entity extraction, budget range understanding, and query expansion.
- **Phase 5: Semantic Recommendations & Alternative Finding**: Near-neighbor retrieval for finding direct substitutes and cheaper alternatives.
- **Phase 6: Personalized Discovery**: User interest vectors and personalized rank re-weighting.

