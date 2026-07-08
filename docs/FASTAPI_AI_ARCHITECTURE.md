# PricePilot — Phase 3 Batch 4
# Distributed AI Microservice Architecture

This document describes the architectural transition of the PricePilot recommendation platform from a monolithic design to a distributed AI-enabled microservice architecture.

---

## 1. Architectural Overview

The target design decouples business logic from machine learning inference. 
* **Spring Boot (Port 8080)** remains the authoritative business backend and orchestration layer (managing authentication, database transactions, watchlists, etc.).
* **FastAPI (Port 8000)** becomes the dedicated stateless AI inference service serving Python-trained recommendation models.

```mermaid
graph TD
    React[React Frontend] -->|REST API| Spring[Spring Boot Orchestration Backend]
    
    subgraph Spring Boot
        SpringController[Recommendation Controller] --> RecommendationService[Recommendation Service]
        RecommendationService --> AiGateway[AI Gateway Service]
        RecommendationService -->|Fallback| RuleEngine[Rule-Based Engine]
        AiGateway --> AiClient[AI HTTP Client]
    end

    subgraph FastAPI Service
        AiClient -->|REST + API Key| FastApi[FastAPI Router]
        FastApi --> PredictionService[Prediction Service]
        PredictionService --> ModelRegistry[Model Registry]
        PredictionService --> Explainer[Explainability Service]
        
        ModelRegistry -->|Loads| Popularity[Popularity Model]
        ModelRegistry -->|Loads| ContentBased[Content Model]
        ModelRegistry -->|Loads| Collaborative[Collaborative Model]
        ModelRegistry -->|Loads| Hybrid[Hybrid Model]
    end
    
    style FastAPI Service fill:#f9f,stroke:#333,stroke-width:2px
```

---

## 2. Request & Prediction Pipeline Flow

When a user requests recommendations, the Spring Boot orchestrator selects candidate items and forwards them along with the user preference profile and recent interactions to FastAPI:

```mermaid
sequenceDiagram
    autonumber
    actor User as React Client
    participant Spring as Spring Boot
    participant DB as PostgreSQL
    participant Gateway as AI Gateway
    participant FastAPI as FastAPI Service
    
    User->>Spring: GET /api/v1/recommendations
    Spring->>DB: Fetch user profile & interaction history
    Spring->>DB: Fetch product candidates (excluding saved/watchlisted)
    Spring->>Gateway: Get recommendations (candidates, profile, interactions)
    
    alt AI Enabled & Healthy
        Gateway->>FastAPI: POST /recommendations/predict (with X-API-Key)
        Note over FastAPI: Convert request into Pandas DataFrame
        Note over FastAPI: Run model inference (Hybrid/Content/Pop)
        Note over FastAPI: Generate explainability reasons
        FastAPI-->>Gateway: 200 OK (Product IDs, Scores, Reasons)
        Gateway-->>Spring: List of Scored Products
    else AI Disabled or Timeout/Error
        Note over Gateway: Catch error & Log Warning
        Gateway->>Spring: Fallback to local Rule-Based Engine
    end
    
    Spring->>DB: Map recommended IDs to full product details (Single query)
    Spring-->>User: JSON Response (Recommendations with score & reasons)
```

---

## 3. Model Lifecycle & Registry

FastAPI implements a dedicated **Model Registry** pattern:
* **Lazy Loading / Startup Loading**: All serialized model picklings (`.pkl` files) are loaded into memory once during FastAPI startup.
* **Stateless Inference**: Models are never re-fitted or reloaded on a per-request basis. Candidate list scoring is executed entirely in-memory using vector operations.
* **Hot Reloading**: The model registry supports atomic hot-reloading. Triggering `/models/reload` loads new models into a temporary workspace and swaps references atomically, preventing inference downtime.

---

## 4. Resilience & Fallback Protocol

Graceful degradation is a core engineering principle in PricePilot. If FastAPI is down or experiencing high latency:

1. **Timeout**: Connection and read timeouts are set on the Spring Boot HTTP client (default: `5000ms`).
2. **Retry Policy**: Transient failures are retried up to `3` times with configured backoffs.
3. **Gateway Circuit Fallback**: If all retries fail or if the service is marked offline:
   * A warning is logged to the orchestrator console.
   * The request automatically falls back to the Java-implemented **Rule-Based Recommendation Engine**.
   * The user receives a valid response (labeled with `Rule-Based` algorithm metadata).
   * **No 500 errors are propagated to the frontend.**

---

## 5. Security & Authentication

Internal FastAPI endpoints are protected and must not be exposed to the public internet:
1. **API Key Security**: The gateway includes the `X-API-Key` header on every call. FastAPI validates this header against `PRICEPILOT_AI_API_KEY`.
2. **Protected Management Endpoints**: Endpoints like `/models/reload` are accessible only internally to system administrators or CI/CD pipelines.

---

## 6. Observability

Observability is handled via multiple layers:
* **Request IDs**: Every HTTP call generates a unique `X-Request-ID` header, propagated through logs to trace requests from Spring Boot to FastAPI.
* **Latency Histograms**: The `/metrics` endpoint exposes Prometheus-compatible metrics tracking request counts and prediction latencies.
* **Structured Logging**: Logs are emitted as structured JSON objects containing timestamps, execution time, model version, and status flags.
