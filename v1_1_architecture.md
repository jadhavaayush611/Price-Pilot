# PricePilot v1.1 — Architecture & Design Specification

## Overview

PricePilot v1.1 introduces the **Shopping Intelligence Architecture & Foundation**, establishing a scalable, decoupled module for multi-dimensional product matrix comparison, AI recommendation engine v2 interfaces, price analytics diagnostics, and Flyway database support.

All endpoints adhere strictly to the standardized versioned API prefix convention: `/api/v1/...`.

---

## 1. Package Architecture & Diagrams

```mermaid
graph TD
    subgraph com.pricepilot.intelligence
        subgraph comparison
            ComparisonController --> ComparisonService
            ComparisonServiceImpl ..|> ComparisonService
            ComparisonServiceImpl --> ComparisonSessionRepository
            ComparisonServiceImpl --> SavedComparisonRepository
        end
        subgraph recommendation
            IntelligenceRecommendationController --> RecommendationService
            RecommendationServiceImpl ..|> RecommendationService
            RecommendationServiceImpl --> RecommendationMetadataRepository
        end
        subgraph analytics
            IntelligenceAnalyticsController --> PriceAnalyticsService
            PriceAnalyticsServiceImpl ..|> PriceAnalyticsService
        end
    end

    subgraph com.pricepilot.ai.v2
        RecommendationPipeline
        ScoringStrategy
        ExplanationGenerator
        RecommendationScore
        RecommendationExplanation
    end

    RecommendationServiceImpl --> RecommendationPipeline
```

### Module Hierarchy
- `com.pricepilot.intelligence.comparison`
  - `ComparisonService`: Interface for product matrix comparisons.
  - `ComparisonServiceImpl`: Service implementation for building rows, scores, and managing sessions.
  - `ComparisonController`: REST endpoints (`GET /api/v1/compare`, `POST /api/v1/compare`).
  - `entity`: `ComparisonSessionEntity`, `SavedComparisonEntity`.
  - `repository`: `ComparisonSessionRepository`, `SavedComparisonRepository`.
  - `dto`: `ComparisonRequest`, `ComparisonResponse`, `ComparisonRow`.
- `com.pricepilot.intelligence.recommendation`
  - `RecommendationService`: Interface for Shopping Intelligence v2 recommendations.
  - `RecommendationServiceImpl`: Foundation implementation wiring matrix scores and explanations.
  - `IntelligenceRecommendationController`: REST endpoint (`GET /api/v1/recommendations/{productId}`).
  - `entity`: `RecommendationMetadataEntity`.
  - `repository`: `RecommendationMetadataRepository`.
  - `dto`: `RecommendationResponse`, `ProductScore`.
- `com.pricepilot.intelligence.analytics`
  - `PriceAnalyticsService`: Interface for price volatility and market diagnostics.
  - `PriceAnalyticsServiceImpl`: Implementation wrapper.
  - `IntelligenceAnalyticsController`: REST endpoint (`GET /api/v1/analytics/{productId}`).
- `com.pricepilot.ai.v2`
  - `RecommendationPipeline`: Core pipeline execution contract.
  - `ScoringStrategy`: Strategy pattern interface for custom scoring algorithms (Phase 2).
  - `ExplanationGenerator`: Interface for natural language AI explanations (Phase 2).
  - `RecommendationScore` & `RecommendationExplanation`: Immutability record types for model outputs.

---

## 2. Service Relationships & Control Flow

1. **Product Comparison Flow**:
   - `ComparisonController` receives `GET /api/v1/compare` or `POST /api/v1/compare`.
   - `ComparisonServiceImpl` retrieves product entities via `ProductService`, extracts lowest prices, calculates `ProductScore` breakdown, builds `ComparisonRow` instances, and persists comparison session.

2. **Recommendation Engine v2 Flow**:
   - `IntelligenceRecommendationController` receives `GET /api/v1/recommendations/{productId}`.
   - `RecommendationServiceImpl` calls `RecommendationPipeline` contract, maps scoring breakdown, and embeds AI decision explanation tags.

3. **Analytics Diagnostics Flow**:
   - `IntelligenceAnalyticsController` receives `GET /api/v1/analytics/{productId}`.
   - `PriceAnalyticsServiceImpl` returns volatility score, trending score, view counts, and deal quality ratings.

---

## 3. Standardized REST API Contracts (/api/v1)

### GET /api/v1/compare
- **Parameters**: `ids` (comma-separated UUIDs), `sessionId` (optional UUID)
- **Response** (`ComparisonResponse`):
```json
{
  "comparisonId": "550e8400-e29b-41d4-a716-446655440000",
  "products": [ /* ProductResponseDTO array */ ],
  "rows": [
    {
      "featureName": "Best Price",
      "category": "Pricing",
      "valuesByProductId": {
        "550e8400-e29b-41d4-a716-446655440001": "$1099"
      },
      "isHighlight": true
    }
  ],
  "scores": {
    "550e8400-e29b-41d4-a716-446655440001": {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "iPhone 15 Pro",
      "overallScore": 88.5,
      "priceValueScore": 90.0,
      "featureScore": 85.0,
      "popularityScore": 92.0,
      "breakdown": { "PriceValue": 90.0 },
      "recommendationBadge": "TOP PICK"
    }
  },
  "summary": "Comparing 2 products across pricing, brand authority, and deal quality.",
  "createdAt": "2026-07-24T10:55:00Z"
}
```

### POST /api/v1/compare
- **Request Body** (`ComparisonRequest`):
```json
{
  "productIds": ["550e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440002"],
  "category": "Electronics",
  "criteria": ["Price", "Brand"]
}
```

### GET /api/v1/recommendations/{productId}
- **Parameters**: `limit` (default: 10)
- **Response** (`RecommendationResponse`):
```json
{
  "targetProductId": "550e8400-e29b-41d4-a716-446655440001",
  "userId": null,
  "recommendedProducts": [ /* Array of ProductResponseDTO */ ],
  "scores": [ /* Array of ProductScore */ ],
  "explanation": "Recommendations generated using similarity matrix and trending demand factors.",
  "strategyUsed": "V2_FOUNDATION_PIPELINE",
  "generatedAt": "2026-07-24T10:55:00Z"
}
```

### GET /api/v1/analytics/{productId}
- **Response** (`ProductAnalyticsResponseDTO`):
```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440001",
  "viewCount": 142,
  "saveCount": 25,
  "watchlistCount": 12,
  "priceChangeCount": 5,
  "trendingScore": 88.5
}
```

---

## 4. Database Schema & Migration Design

Flyway migration: `V1.11__create_shopping_intelligence_tables.sql`

```sql
CREATE TABLE comparison_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(100),
    product_ids TEXT,
    title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE saved_comparisons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID REFERENCES comparison_sessions(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    product_ids TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recommendation_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    model_version VARCHAR(50) NOT NULL,
    algorithm_type VARCHAR(50) NOT NULL,
    score_factors JSONB,
    confidence_score NUMERIC(5,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Database Design Principles & Future Evolution

1. **Recommendation Metadata Scope**:
   - `recommendation_metadata` remains focused exclusively on model versioning, feature weights, and scoring configuration to prevent it from becoming an unindexed dumping ground.
   - Historical model inference execution outputs will be logged to a dedicated `recommendation_history_events` table in Phase 2.

2. **Comparison Session Normalization Roadmap**:
   - In v1.1, `comparison_sessions` uses `product_ids TEXT` for simple session persistence.
   - In v1.2+, as comparison complexity increases, this will be normalized into `comparison_sessions` and `comparison_session_products(session_id, product_id, display_order)` to enable unconstrained product counts, indexed joins, and relational filtering.

3. **Security Policy Boundaries**:
   - Public read access permitted for anonymous matrix lookup (`GET /api/v1/compare`), product recommendations (`GET /api/v1/recommendations/{productId}`), and public analytics (`GET /api/v1/analytics/{productId}`).
   - Authentication strictly enforced for saved comparison matrices (`/api/v1/compare/save`), personalized user recommendations (`/api/v1/recommendations/personalized`), and user session history (`/api/v1/intelligence/sessions/**`).

---

## 5. Future Extension Points & Phase Roadmap

1. **Recommendation Engine v2 ML Integration (Phase 2)**:
   - Implement `ScoringStrategy` for hybrid vector embeddings (e.g. `VectorSearchScoringStrategy`, `CollaborativeFilteringScoringStrategy`).
   - Implement `ExplanationGenerator` powered by LLM / Python AI Gateway integration.
2. **Advanced Analytics & Price Prediction (Phase 3)**:
   - Wire `PriceAnalyticsService` to time-series forecasting models (ARIMA / Prophet / ML model endpoint).
3. **Frontend Visualization Enhancement (Phase 4)**:
   - Connect Recharts/D3 price history graphs into `AnalyticsPage` shell.
