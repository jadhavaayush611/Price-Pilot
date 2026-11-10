# PricePilot v1.4 — AI Chatbot Implementation Plan (AI-01 to AI-11) (Final Master Revision)

**Target Milestone**: PricePilot v1.4  
**Companion Audit**: `AI_CHATBOT_ARCHITECTURE_AUDIT.md`  
**Architecture Baseline**: Hybrid Architecture (Spring Boot Orchestrator + FastAPI Inference Gateway + PostgreSQL Durable Store)

---

## 1. Modular Hybrid Architecture & Boundary Diagram

```mermaid
graph TD
    User([User Browser - React 19 / AiAssistantPage])
    
    subgraph Spring Boot 3.4 Application Orchestrator (Port 8080)
        Ctrl[AssistantStreamController]
        Auth[Spring Security / JWT Guard]
        Orch[ShoppingAssistantOrchestrator]
        Mem[PostgreSQL 4-Tier Memory Manager]
        ToolEng[Secure Tool Execution Engine]
        ClaimBuffer[Claim Buffer & Grounding Validator]
        Fallback[DeterministicShoppingAssistantFallback]
        SSE[SSE Stream Emitter]
    end

    subgraph FastAPI AI Inference Gateway (Port 8000)
        GatewayRouter[/assistant/generate_stream]
        GeminiAdapter[Gemini Provider Adapter]
    end

    subgraph External LLM Provider
        GeminiAPI[Google Gemini 3.8 Flash Candidate API]
    end

    subgraph Spring Boot Domain Services
        SearchSvc[SearchDiscoveryService]
        CompareSvc[ComparisonService]
        OfferSvc[MarketplaceOfferAggregatorService]
        HistSvc[PriceHistoryService]
        TrendSvc[PriceTrendAnalyzer]
        AlertSvc[PriceAlertService]
        PrefSvc[UserShoppingPreferenceService]
    end

    subgraph PostgreSQL 16 Source of Truth
        PG_Conv[(assistant_conversations)]
        PG_Msg[(assistant_messages)]
        PG_Pref[(user_shopping_preferences)]
        PG_Plan[(assistant_shopping_plans)]
        PG_Action[(assistant_staged_actions)]
    end

    User -->|POST /api/v1/assistant/conversations/{id}/stream| Ctrl
    Ctrl --> Auth
    Auth --> Orch
    
    Orch <--> Mem
    Mem <--> PG_Conv
    Mem <--> PG_Msg
    Mem <--> PG_Pref
    Mem <--> PG_Plan
    
    Orch -->|POST /assistant/generate_stream (X-API-Key)| GatewayRouter
    GatewayRouter --> GeminiAdapter
    GeminiAdapter -->|Stream Chunks + Tool Declarations| GeminiAPI
    
    GeminiAPI -->|Tool Call Event| GeminiAdapter
    GeminiAdapter -->|HTTP SSE Event: tool_call| Orch
    Orch --> ToolEng
    
    ToolEng --> SearchSvc
    ToolEng --> CompareSvc
    ToolEng --> OfferSvc
    ToolEng --> HistSvc
    ToolEng --> TrendSvc
    ToolEng --> AlertSvc
    ToolEng --> PrefSvc
    
    ToolEng -->|Tool Execution Output| Orch
    Orch -->|POST Tool Results| GatewayRouter
    GatewayRouter --> GeminiAdapter
    GeminiAdapter -->|Continue Generation| GeminiAPI
    
    GeminiAPI -->|Prose Tokens & Claims| GeminiAdapter
    GeminiAdapter -->|SSE Chunks| Orch
    Orch --> ClaimBuffer
    ClaimBuffer -->|Validated Chunks & Verified Evidence| SSE
    SSE -->|text/event-stream| User
    
    Orch -.->|On Timeout or Upstream 5xx| Fallback
    Fallback -.->|Deterministic Evidence Prose| SSE
```

---

## 2. Explicit API and SSE Event Contracts

### 2.1 Spring Boot REST & Streaming Endpoints

#### Canonical Versioned Endpoints:
1. `POST /api/v1/assistant/conversations`
   - **Request**: `{ "title": "New Shopping Discussion" }`
   - **Response**: `201 Created` -> `AssistantConversationDTO`
2. `GET /api/v1/assistant/conversations`
   - **Response**: `200 OK` -> `List<AssistantConversationDTO>`
3. `GET /api/v1/assistant/conversations/{id}`
   - **Response**: `200 OK` -> `AssistantConversationDTO` (with ordered `messages`)
4. `DELETE /api/v1/assistant/conversations/{id}`
   - **Response**: `204 No Content` (cascades deletion to `assistant_messages`)
5. `POST /api/v1/assistant/conversations/{id}/messages`
   - **Preserved Synchronous API**: `{ "content": "...", "activeProductId": "..." }`
   - **Response**: `200 OK` -> `AssistantResponseDTO`
6. `POST /api/v1/assistant/conversations/{id}/stream`
   - **Canonical SSE Streaming Endpoint (Introduced in AI-03)**:
   - **Headers**: `Authorization: Bearer <JWT>`, `Accept: text/event-stream`, `Content-Type: application/json`
   - **Request Body**:
     ```json
     {
       "content": "Is now a good time to buy the Sony WH-1000XM4?",
       "activeProductId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
     }
     ```
   - **Response**: `200 OK` -> `text/event-stream`
7. `POST /api/v1/assistant/conversations/{id}/actions/{stagedActionId}/confirm`
   - **Headers**: `Authorization: Bearer <JWT>`, `Content-Type: application/json`
   - **Request Body**:
     ```json
     {
       "confirmationToken": "sec_tok_9f83b2a1c0d4e5f6789a..."
     }
     ```
   - **Response**: `200 OK` -> `{ "status": "CONFIRMED", "stagedActionId": UUID, "result": {...} }`
8. `POST /api/v1/assistant/conversations/{id}/actions/{stagedActionId}/reject`
   - **Headers**: `Authorization: Bearer <JWT>`
   - **Response**: `200 OK` -> `{ "status": "REJECTED", "stagedActionId": UUID }`

#### Backward-Compatible Legacy Endpoints (Preserved for SDKs & Existing Tests):
- `POST /api/v1/assistant/chat`
- `POST /api/v1/assistant/compare`
- `POST /api/v1/assistant/ask`
- `POST /api/v1/assistant/clear_memory`

---

### 2.2 Server-Sent Events (SSE) Event Specifications

Frames adhere to standard SSE formatting: `event: <event_type>\ndata: <json_payload>\n\n`

```
┌──────────────────┬───────────────────────────────────────────┬──────────────────────────────────────────────────────┐
│ Event Name       │ Payload Schema                            │ Description                                          │
├──────────────────┼───────────────────────────────────────────┼──────────────────────────────────────────────────────┤
│ token            │ { "delta": string }                       │ Text chunk for incremental typewriter display.       │
│ tool_call_start  │ { "tool": string, "arguments": object }   │ Emitted when model invokes a tool.                   │
│ tool_call_result │ { "tool": string, "summary": string }     │ Emitted when Spring Boot completes tool execution.   │
│ evidence_bundle  │ { "evidenceId": UUID, "products": [...] } │ Authoritative verified entities for UI cards.        │
│ action_proposal  │ { "stagedActionId": UUID, "summary": ... }│ Emitted when a mutating action requires confirm.     │
│ done             │ { "messageId": UUID, "durationMs": number}│ Emitted when generation and persistence complete.    │
│ error            │ { "code": string, "message": string }     │ Emitted on non-recoverable error.                    │
└──────────────────┴───────────────────────────────────────────┴──────────────────────────────────────────────────────┘
```

#### Detailed Action Proposal Payload & Secure Confirmation Protocol
To ensure security, a UUID is **never** treated as a secret:
```yaml
event: action_proposal
data: {
  "stagedActionId": "8b1d4c20-7f28-4e89-a29d-476c9ef5d112",
  "confirmationToken": "sec_tok_9f83b2a1c0d4e5f6789a4b2c1d0e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e",
  "actionType": "CREATE_PRICE_ALERT",
  "summary": "Set price alert for Sony WH-1000XM4 at $229.00",
  "expiresAt": "2026-11-09T21:40:00Z",
  "expiresInSeconds": 900
}
```
**Verification Protocol (Locked)**:
1. `confirmationToken` is a cryptographically secure, single-use, 256-bit random string generated via `SecureRandom` on the server and delivered exclusively over the authenticated TLS connection in the SSE frame.
2. In PostgreSQL (`assistant_staged_actions`), Spring Boot stores **only the SHA-256 hash** of the token (`token_hash`), never the raw token string.
3. Upon receiving `/actions/{stagedActionId}/confirm`:
   - Validates user identity from the JWT Bearer token.
   - Verifies the staged action belongs to the authenticated `userId` and `conversationId`.
   - Checks that status is `STAGED` and `now() < expires_at` (15-minute window).
   - Computes SHA-256 of the incoming `confirmationToken` and verifies against `token_hash` using constant-time comparison.
   - Atomically updates status to `EXECUTED` and executes the mutation through the Spring Boot domain service.

---

## 3. Operational Timeout & Retry Budget

```
┌───────────────────────────────────────────────────────────────────────────────────────┐
│                              UNIFIED TIMEOUT & RETRY BUDGET                           │
├───────────────────────────┬───────────────────────────┬───────────────────────────────┤
│ Operational Parameter     │ Synchronous Flow (AI-01)  │ Streaming Flow (AI-03+)       │
├───────────────────────────┼───────────────────────────┼───────────────────────────────┤
│ Connect Timeout           │ 1,000 ms                  │ 1,000 ms                      │
│ Read Timeout              │ 5,000 ms                  │ 4,000 ms (TTFT), 2,000ms/chunk│
│ Retries on Transient 429  │ 0 retries (fail fast)     │ 1 retry (500 ms backoff)      │
│ Circuit Breaker (R4j)     │ 50% fail / 10 reqs -> 30s │ 50% fail / 10 reqs -> 30s     │
│ Maximum Total Wait        │ 5.0 seconds strictly      │ 8.0 seconds total wait        │
│ Fallback Behavior         │ DeterministicFallback     │ DeterministicFallback (SSE)   │
└───────────────────────────┴───────────────────────────┴───────────────────────────────┘
```
**Policy**: Retries must never cause the total request duration to exceed the 5.0s (synchronous) or 8.0s (streaming) user-facing ceiling. Once exhausted, the system immediately drops to `DeterministicShoppingAssistantFallback`.

---

## 4. Final Reconciled Tool Registry Specification

All 9 tools execute strictly through Spring Boot domain services:

```
┌────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   RECONCILED TOOL REGISTRY                                     │
├────┬─────────────────────────────┬───────────┬───────────────┬──────────────────┬──────────────┤
│ #  │ Tool Name                   │ Nature    │ Authorization │ Execution Mode   │ Confirmation │
├────┼─────────────────────────────┼───────────┼───────────────┼──────────────────┼──────────────┤
│ 1  │ search_products             │ Read-Only │ Public/User   │ Auto-Executable  │ None         │
│ 2  │ compare_products            │ Read-Only │ Public/User   │ Auto-Executable  │ None         │
│ 3  │ get_recommendations         │ Read-Only │ Authenticated │ Auto-Executable  │ None         │
│ 4  │ get_marketplace_offers      │ Read-Only │ Public/User   │ Auto-Executable  │ None         │
│ 5  │ get_price_history           │ Read-Only │ Public/User   │ Auto-Executable  │ None         │
│ 6  │ get_price_forecast          │ Read-Only │ Public/User   │ Auto-Executable  │ None         │
│ 7  │ get_user_preferences        │ Read-Only │ Authenticated │ Auto-Executable  │ None         │
│ 8  │ manage_price_alert          │ Mutating  │ Authenticated │ Two-Phase Commit │ Mandatory    │
│ 9  │ manage_shopping_plan        │ Mutating  │ Authenticated │ Two-Phase Commit │ Mandatory    │
└────┴─────────────────────────────┴───────────┴───────────────┴──────────────────┴──────────────┘
```

### Detailed Tool Invariants & Integrity Boundaries

1. **`search_products`**
   - **Service**: `SearchDiscoveryService.searchAndDiscover()`
   - **Input Schema**: `query` (string, opt), `category` (string, opt), `brand` (string, opt), `minPrice` (number, opt), `maxPrice` (number, opt), `currency` (string, opt), `limit` (int, default: 5, max: 10).
   - **Output**: `{ totalFound: int, products: [...] }`.
2. **`compare_products`**
   - **Service**: `ComparisonService.compareProducts()`
   - **Input Schema**: `productIds` (UUID[], min: 2, max: 4, req), `targetCurrency` (string, opt).
   - **Output**: `{ products: [...], priceDifferences: [...], bestValueProductId: UUID, comparisonMatrix: {...} }`.
3. **`get_recommendations`**
   - **Service**: `RecommendationService.getPersonalizedRecommendations()`
   - **Input Schema**: `category` (string, opt), `limit` (int, default: 5, max: 10).
   - **Output**: `{ recommendations: List of { product: {...}, score: float, reasoning: string } }`.
4. **`get_marketplace_offers`**
   - **Service**: `MarketplaceOfferAggregatorService.aggregateOffers()`
   - **Input Schema**: `productId` (UUID, req), `includeStale` (bool, default: false).
   - **Provenance & Freshness Separation (Locked)**:
     - **Source Provenance (`provenanceType`)**: `LIVE` (real-time verified external feed), `OFFLINE_SNAPSHOT` (persisted catalog database record), or `SIMULATED` (offline mock adapter).
     - **Freshness Status (`verificationStatus`)**:
       - `FRESH`: Observation age $\le 168$ hours (604,800 seconds).
       - `STALE`: Observation age strictly $> 168$ hours. (At exactly 168 hours, it is not stale per `OfferNormalizer.java`).
       - `UNVERIFIED`: Observation timestamp missing or unconfirmed.
   - **Safety Boundary**: If adapters fail, returns `{ status: "MARKETPLACE_UNAVAILABLE", offers: [] }`. **Catalog baseline prices are never presented as seller offers**.
5. **`get_price_history`**
   - **Service**: `PriceHistoryService.getPriceHistory()`
   - **Input Schema**: `productId` (UUID, req), `windowDays` (int, default: 90, enum: [30, 60, 90, 180, 365]).
   - **Output**: `{ productId: UUID, minPrice, maxPrice, avgPrice, observationCount, pricePoints: List of { recordedAt, price, sellerId } }`.
   - **Requirement**: Strictly observed past measurements (`HistoricalPricePoint`). Zero projections.
6. **`get_price_forecast`**
   - **Service Integration & Alignment**:
     - Consumes canonical v1.4 statistical forecasting engine when available (including `modelName`, `modelVersion`, `forecastHorizonDays`, `uncertaintyInterval`, `provenance`, and `reliabilityScore`).
     - **Pre-Engine Interim**: Label result strictly as a **`TREND_ESTIMATE`** (calculation method: `WINDOWED_BASELINE_PERCENTAGE`)—**NOT a statistical forecast**.
     - **Integrity Rule**: When observations < 3, **must return `trend: "INSUFFICIENT_DATA"` with `percentageChange: null`, `buyConfidenceScore: null`, `uncertaintyInterval: null`, and `reliabilityScore: null`**. Never return an arbitrary default score (e.g. 70%).
7. **`get_user_preferences`**
   - **Service**: `UserShoppingPreferenceService.getPreferences()`
   - **Input Schema**: `{}` (Empty; principal-bound).
   - **Output**: `{ userId: UUID, currency: string, maxBudget: number, preferredBrands: string[] }`.
8. **`manage_price_alert`** (Mutating — User Confirmation Mandatory)
   - **Service**: `PriceAlertService`
   - **Actions**: `PROPOSE_CREATE`, `PROPOSE_DELETE`, `EXECUTE_CONFIRMED`.
   - **Confirmation Rule**: Every mutation (both creation and deletion) requires two-phase confirmation using the single-use token protocol with a 15-minute countdown.
9. **`manage_shopping_plan`** (Mutating — User Confirmation Mandatory)
   - **Service**: `ShoppingPlanService`
   - **Actions**: `PROPOSE_CREATE`, `PROPOSE_UPDATE_STATUS`, `EXECUTE_CONFIRMED`, `GET_ACTIVE`.
   - **Safety Boundary**: No purchasing or payment execution. Background monitoring uses only authorized internal data sources.

---

## 5. PostgreSQL 4-Tier Memory Management

### 5.1 Memory Retrieval Query & Bidirectional Index Support

To build the conversational context window:
1. Retrieve the latest 10 messages in descending order:
   ```sql
   SELECT * FROM assistant_messages 
   WHERE conversation_id = :conversationId 
   ORDER BY created_at DESC 
   LIMIT 10;
   ```
2. **Reverse the list in memory** (`Collections.reverse(list)`) in Spring Boot to restore chronological ascending order for prompt assembly.
3. **Index Support**: Existing Flyway migration `V1.16` contains `idx_assistant_msg_conv_created ON assistant_messages(conversation_id, created_at ASC)`. In PostgreSQL, standard B-tree indexes support bidirectional scanning (`Index Scan Backward`). Query execution latency will be benchmarked under load testing in staging.
4. **Zero Redis**: All conversation state is persisted durably in PostgreSQL.

---

## 6. Phase-by-Phase Implementation Plan (AI-01 through AI-11)

---

### AI-01: Architecture Alignment, Security Remediation & Synchronous Integration
- **Dependencies**: None.
- **Scope**:
  1. **Security Fix in `AssistantController.java`**: Remove lines 253–278.
     - Unauthenticated requests (no JWT, invalid JWT, expired signature) return **`401 Unauthorized`**.
     - Authenticated requests where the JWT principal cannot be resolved in `users` table throw `AccessDeniedException` -> **`403 Forbidden`**.
     - Neither case creates a user automatically.
  2. **Synchronous Integration Path**: Wire `ShoppingAssistantServiceImpl.sendMessage()` to call FastAPI's existing synchronous chat route (`POST /assistant/chat`) via `AiClient.chat()` within the **5.0-second total timeout budget** (0 retries).
  3. **Circuit-Breaker Fallback**: Wrap the call in Resilience4j CircuitBreaker; if FastAPI is unreachable or takes > 5.0s, gracefully fall back to `DeterministicShoppingAssistantFallback` with `fallbackMode: true` in the response DTO.
  4. **Contract Preservation**: Keep `POST /conversations/{id}/messages` and all legacy endpoints (`/chat`, `/compare`, `/ask`) 100% backward-compatible. (Do NOT introduce streaming yet).
- **Acceptance Tests**:
  - `[TEST-01.1]`: Unauthenticated requests return `401 Unauthorized`. Valid JWT with non-existent user returns `403 Forbidden`. Zero user rows are created.
  - `[TEST-01.2]`: Primary conversation endpoint executes synchronous AI client call within 5.0 seconds.
  - `[TEST-01.3]`: When AI service times out (> 5.0s) or fails, graceful fallback generates deterministic evidence prose.

---

### AI-02: Gemini Provider Abstraction & Gateway Integration
- **Dependencies**: AI-01.
- **Scope**:
  1. Set target initial candidate: **`gemini-3.8-flash`**.
  2. Verify API availability, structured tool calling, streaming, and response-schema compatibility before coding.
  3. Implement provider abstraction in FastAPI (`LlmGatewayClient`). (Gemini only in v1.4; do not integrate multiple providers yet).
  4. Implement WireMock / recorded mock fixtures for automated CI/CD. Never require a live Gemini API key for CI. Live calls optional in staging only.
  5. Configure CircuitBreaker with 50% failure rate over 10 requests tripping open for 30 seconds.
- **Acceptance Tests**:
  - `[TEST-02.1]`: CI tests run 100% offline using WireMock mock fixtures without requiring a live Gemini API key.
  - `[TEST-02.2]`: Gateway successfully formats requests for `gemini-3.8-flash`.

---

### AI-03: Reactive Streaming over HTTP POST (SSE)
- **Dependencies**: AI-02.
- **Scope**:
  1. Implement `POST /api/v1/assistant/conversations/{id}/stream` in Spring Boot emitting `text/event-stream`.
  2. Emit typed SSE frames (`token`, `tool_call_start`, `tool_call_result`, `evidence_bundle`, `action_proposal`, `done`, `error`).
  3. Enforce streaming timeout budget: TTFT timeout = 4,000 ms, chunk read timeout = 2,000 ms, max 1 retry on 429/503 (500 ms backoff), strictly capped at 8.0 seconds total wait.
  4. Update frontend `apiService` using `fetch()` and `ReadableStream`.
  5. Implement client-side `AbortController` cancellation propagating to backend.
  6. Preserve synchronous `/messages` endpoint in parallel.
- **Acceptance Tests**:
  - `[TEST-03.1]`: Measured engineering objective: First token arrives at client in **< 800 ms** in benchmarked test environment.
  - `[TEST-03.2]`: Aborting the request halts server inference and releases resources within 100 ms.

---

### AI-04: Structured Tool Registry (Read-Only Tools 1–7)
- **Dependencies**: AI-03.
- **Scope**:
  1. Implement Spring Boot `ToolExecutionEngine` declaring Gemini function schemas for Tools 1 through 7 (`search_products`, `compare_products`, `get_recommendations`, `get_marketplace_offers`, `get_price_history`, `get_price_forecast`, `get_user_preferences`).
  2. Wire tools directly to Spring Boot domain services.
  3. Enforce maximum 3 tool iterations per conversational turn.
  4. Implement Tool 4 (`get_marketplace_offers`) preserving the 168-hour stale threshold and distinguishing `LIVE`, `OFFLINE_SNAPSHOT`, `STALE`, and `SIMULATED`. If adapters fail, return `{ status: "MARKETPLACE_UNAVAILABLE", offers: [] }`.
  5. Implement Tool 6 (`get_price_forecast`): label pre-engine result as `TREND_ESTIMATE`. Return `INSUFFICIENT_DATA` with `null` confidence when observations < 3.
- **Acceptance Tests**:
  - `[TEST-04.1]`: Query "Compare Sony WH-1000XM4 and Bose QC45" invokes `compare_products` with validated UUIDs.
  - `[TEST-04.2]`: `get_price_forecast` on a product with 1 price point returns `INSUFFICIENT_DATA` and `null` confidence score (never 70%).
  - `[TEST-04.3]`: `get_marketplace_offers` correctly labels offers older than 168 hours as `STALE`.

---

### AI-05: Human-in-the-Loop Mutating Actions (Tools 8–9)
- **Dependencies**: AI-04.
- **Scope**:
  1. Create database table `assistant_staged_actions` (`id`, `user_id`, `conversation_id`, `action_type`, `payload`, `token_hash`, `expires_at`, `status`).
  2. Implement Tools 8 and 9 (`manage_price_alert`, `manage_shopping_plan`) using two-phase staging (`PROPOSE` -> `EXECUTE`).
  3. Enforce confirmation on ALL mutations, including alert deletion and plan status updates.
  4. Implement confirmation endpoints: `/conversations/{id}/actions/{stagedActionId}/confirm` and `/reject` validating single-use token hash and 15-minute expiration.
  5. Show 15-minute countdown in UI and enforce expiration on backend.
  6. Strict constraint: No purchasing or payment execution in v1.4.
- **Acceptance Tests**:
  - `[TEST-05.1]`: Request to create or delete an alert emits `action_proposal` with a 15-minute expiry. Mutation is not executed until confirmed.
  - `[TEST-05.2]`: Staged actions older than 15 minutes are rejected upon confirmation attempt.
  - `[TEST-05.3]`: Confirmation tokens are single-use, action-scoped, and validated server-side via SHA-256 hash comparison.

---

### AI-06: PostgreSQL 4-Tier Memory Management
- **Dependencies**: AI-01, AI-05.
- **Scope**:
  1. Remove volatile in-memory dictionary `ConversationMemory` from FastAPI gateway.
  2. Implement PostgreSQL-backed memory retrieval:
     - Query latest 10 messages `ORDER BY created_at DESC LIMIT 10`, then reverse in memory.
     - Evaluate `idx_assistant_msg_conv_created` B-tree index scan performance via staging benchmark.
     - Read `user_shopping_preferences` and active `assistant_shopping_plans`.
     - Compress conversations > 10 turns into `assistant_conversation_summaries`.
  3. Retain deterministic client-side conversation titling (`assistantTitle.ts`).
- **Acceptance Tests**:
  - `[TEST-06.1]`: Restarting FastAPI or Spring Boot leaves active conversational context 100% intact from PostgreSQL.
  - `[TEST-06.2]`: Multi-turn pronoun references correctly resolve product IDs across turns.

---

### AI-07: Structured Evidence Grounding & Zero-Hallucination Guardrail
- **Dependencies**: AI-04.
- **Scope**:
  1. Implement `StructuredEvidenceGroundingValidator` in Spring Boot.
  2. Tag all tool outputs with server-generated `evidenceId` in the `AssistantEvidenceBundle`.
  3. Treat model citations as untrusted syntax; exact prices and critical values must be rendered in UI directly from verified `evidence_bundle` SSE payloads.
  4. Buffer conversational prose chunks containing numerical claims; validate against server `evidenceId`s before emission. Suppress or replace unverified price claims.
  5. Remove fragile regex grounding check in `PromptInjectionProtector.java`.
- **Acceptance Tests**:
  - `[TEST-07.1]`: Measured engineering objective: **0% price hallucination rate** on synthetic catalog benchmark suite.
  - `[TEST-07.2]`: Model prose with fabricated price claims is intercepted and corrected before streaming to client.

---

### AI-08: Security Hardening & Prompt Defense
- **Dependencies**: AI-01, AI-07.
- **Scope**:
  1. Implement structural XML prompt fencing for user input and catalog data (as supplementary formatting, not security boundary).
  2. Sanitize indirect prompt injection surfaces (scraped seller descriptions, reviews).
  3. Validate IDOR ownership on all conversation, alert, and plan operations.
  4. Implement Spring Security rate limiting (30 requests/minute per authenticated user).
- **Acceptance Tests**:
  - `[TEST-08.1]`: Known adversarial jailbreak prompts do not alter system instructions or extract internal parameters.
  - `[TEST-08.2]`: Attempting to confirm or view another user's staged action returns `403 Forbidden`.

---

### AI-09: Frontend Conversational Streaming UX
- **Dependencies**: AI-03, AI-05.
- **Scope**:
  1. Decompose `AiAssistantPage.tsx` into modular components (`ConversationSidebar`, `MessageList`, `StreamingMessage`, `ToolCallBadge`, `ActionConfirmationCard`).
  2. Connect `StreamingMessage` to SSE `/stream` using `fetch()` and `ReadableStream`.
  3. Render live tool status badges during execution ("Searching catalog...", "Analyzing price history...").
  4. Render interactive confirmation cards with a 15-minute countdown for staged price alerts and shopping plans.
  5. Wire "Stop Generation" button to `AbortController`.
- **Acceptance Tests**:
  - `[TEST-09.1]`: Progressive token streaming renders smoothly without layout jitter.
  - `[TEST-09.2]`: Staged action cards display live 15-minute countdown timer and confirm/reject buttons.

---

### AI-10: Automated Evaluation Suite & Quality Benchmarks
- **Dependencies**: AI-04, AI-07.
- **Scope**:
  1. Build an automated evaluation test harness in `backend/src/test/java/.../evaluation/` running against WireMock fixtures in CI.
  2. Create 50 golden shopping evaluation scenarios covering discovery, comparisons, pricing forecasts, multi-turn contexts, and adversarial injections.
  3. Implement automated scoring for Tool Selection Accuracy, Grounding Precision, and Latency.
- **Acceptance Tests**:
  - `[TEST-10.1]`: Tool selection precision exceeds 95% on golden test dataset.
  - `[TEST-10.2]`: Grounding verification scores 100% on catalog-backed scenarios.

---

### AI-11: Observability & Autonomous Shopping Foundations
- **Dependencies**: AI-05, AI-06.
- **Scope**:
  1. Instrument Micrometer / Prometheus metrics:
     - `pricepilot_ai_stream_ttft_seconds` (Histogram)
     - `pricepilot_ai_tokens_total{model, type}` (Counter)
     - `pricepilot_ai_tool_executions_total{tool, status}` (Counter)
     - `pricepilot_ai_grounding_corrections_total` (Counter)
  2. Implement background `ShoppingPlanExecutionService` that periodically checks active `assistant_shopping_plans` against authorized internal marketplace price updates.
  3. Ensure background monitoring uses only authorized internal data sources.
- **Acceptance Tests**:
  - `[TEST-11.1]`: `/actuator/prometheus` exposes active LLM latency, token counts, and tool metrics.
  - `[TEST-11.2]`: When internal catalog price drops below a plan's target budget, the background service triggers an internal notification event.

---

## 7. Locked Human Decisions Record

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           LOCKED HUMAN DECISIONS RECORD                         │
├────┬─────────────────────────────┬──────────────────────────────────────────────┤
│ #  │ Topic                       │ Locked Decision                              │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 1  │ CI/CD Testing Policy        │ WireMock / recorded mock fixtures in CI.     │
│    │                             │ Never require a live API key for CI. Live    │
│    │                             │ calls optional in staging only.              │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 2  │ Conversation Titling        │ Retain client-side deterministic generation  │
│    │                             │ (assistantTitle.ts) for v1.4.                │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 3  │ Staged Action Expiration    │ 15 minutes. Display countdown timer in UI    │
│    │                             │ and enforce server-side expiration.          │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 4  │ Initial Model Candidate     │ gemini-3.8-flash (gemini-1.5-flash is shut   │
│    │                             │ down). Verify API availability, tool calls,  │
│    │                             │ streaming, and schemas before coding.        │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 5  │ Grounding & Streaming Sync  │ Server evidenceId is ground truth; model     │
│    │                             │ citations are untrusted syntax. Critical     │
│    │                             │ values rendered from structured bundle.      │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 6  │ Marketplace Freshness       │ Preserve PricePilot's 168-hour stale window. │
│    │                             │ Separate provenance (LIVE/OFFLINE/SIMULATED) │
│    │                             │ from freshness (FRESH/STALE/UNVERIFIED).     │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 7  │ Forecast Integrity          │ Pre-engine: label as TREND_ESTIMATE. Return  │
│    │                             │ INSUFFICIENT_DATA and null confidence when   │
│    │                             │ observations < 3. Never return 70%.          │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 8  │ Memory Retrieval            │ ORDER BY created_at DESC LIMIT 10, reversed  │
│    │                             │ in memory. PostgreSQL B-tree scan. No Redis. │
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 9  │ Timeout Budget Reconciliation│ Sync = 5.0s max wait (0 retries). Streaming  │
│    │                             │ = 8.0s max wait (1 retry, 500ms backoff).    │
│    │                             │ CircuitBreaker trips to deterministic fallback│
├────┼─────────────────────────────┼──────────────────────────────────────────────┤
│ 10 │ AI-01 Migration Sequence    │ Synchronous integration with fallback first; │
│    │                             │ introduce streaming in AI-03. No broken API. │
└────┴─────────────────────────────┴──────────────────────────────────────────────┘
```
