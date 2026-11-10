# PricePilot v1.4 — AI Chatbot Architecture & Quality Audit (Final Master Revision)

**Target Milestone**: PricePilot v1.4  
**Date of Audit**: November 2026  
**Audit Scope**: End-to-End Chatbot Architecture, Model Integration, Conversation Lifecycle, Streaming, Tool Calling, Memory, Reliability, Security, and Quality Evaluation  
**Operating Mode**: Read-Only Comprehensive Audit  
**Architecture Baseline**: Hybrid Architecture (Spring Boot Orchestrator + FastAPI Inference Gateway + PostgreSQL Durable Store)

---

## Conventions & Classification Legend

To maintain strict architectural rigor, all statements throughout this audit are tagged according to the following taxonomy:
- `[VERIFIED FACT]`: Direct, confirmed observation from source code, configuration files, migration scripts, or existing test suites.
- `[RECOMMENDATION]`: Prescribed architectural change, industry best practice, or mitigation to be executed in v1.4.
- `[ASSUMPTION]`: Explicit hypothesis based on common deployment environments or configurations where code leaves room for variance.
- `[UNVERIFIED CAPABILITY]`: Capability documented in preliminary design notes or comments but absent or non-functional in the current implementation.

---

## Executive Summary & Audit Synthesis

PricePilot v1.3 established robust domain intelligence capabilities, including multi-seller catalog normalization, deterministic price trend calculations, and offline marketplace offer adapters. However, the conversational interface currently present in PricePilot operates across an **architectural dichotomy**:

1. `[VERIFIED FACT]` **The Dual-Stack Architectural Split**: There are two distinct, disconnected assistant implementations:
   - **Modern Spring Boot Versioned Flow** (`/api/v1/assistant/conversations/{id}/messages`): Invoked by the React frontend (`AiAssistantPage.tsx`). It generates answers **exclusively through deterministic template strings** via `DeterministicShoppingAssistantFallback.java`. The configured external AI client is **never invoked** in this primary path.
   - **Legacy FastAPI Flow** (`/api/v1/assistant/chat`, `/compare`, `/ask`): Implemented in the Python microservice (`pricepilot-ai/app/assistant/orchestrator.py`). It contains an integration with Google Gemini and a keyword-based pre-retrieval pipeline. However, this flow is only reached if the client invokes the legacy endpoints or as an error fallback.
2. `[VERIFIED FACT]` **Absence of Real Tool Calling**: Neither backend utilizes native LLM function/tool calling. Context retrieval is rigidly performed *before* prompting the model (heuristic pre-RAG). The LLM cannot query the database iteratively, inspect alternate products, verify inventory, or invoke application services.
3. `[VERIFIED FACT]` **Zero Streaming Support**: Neither Spring Boot nor FastAPI supports Server-Sent Events (SSE), WebSockets, or HTTP chunked transfer. Responses are generated in batch and returned as monolithic JSON payloads, producing high Time-To-First-Token (TTFT) and degrading conversational responsiveness.
4. `[VERIFIED FACT]` **In-Memory Volatility & Leaks in Inference Layer**: The Python AI microservice stores conversation history, active product context, and session data in a static in-memory Python dictionary (`ConversationMemory._storage`) without a TTL, cleanup thread, or persistence layer. If restarted or scaled horizontally, all state is lost.
5. `[VERIFIED FACT]` **Security Flaw in Identity Resolution**: Spring Boot's `AssistantController.resolveAuthenticatedUserId()` contains a fallback that automatically creates a real user in the database with hardcoded credentials (`Password123!`) if the Spring Security principal is a string or `UserDetails` without an existing DB record.
6. `[VERIFIED FACT]` **Fragile Regex-Based Grounding**: The existing grounding check in `PromptInjectionProtector.java` attempts to parse currency amounts from unstructured response prose via regular expressions (`PRICE_REGEX`). This approach is brittle, vulnerable to formatting discrepancies, and is completely bypassed in production response generation.
7. `[VERIFIED FACT]` **Historical vs Forecast Conflation**: Existing local fallback code in FastAPI's `orchestrator.py` fabricates an arbitrary 70% confidence score when historical price points are missing, directly violating statistical integrity.

---

## 1. Existing Architecture & Request Lifecycle

### 1.1 Chat Frontend Components & State Management

- **Component Structure**: `[VERIFIED FACT]`
  - Located in `frontend/src/pages/AiAssistantPage.tsx` (~1,020 lines).
  - Handles the dual role of conversation list sidebar (`conversations`) and active thread message view (`messages`).
  - Context Handoff: Reads URL query parameters `?productId=<uuid>` and `?query=<string>` on mount. If `productId` is present, it fetches product details via `apiService.getProduct(productId)` and binds it to `activeProductId`. If `query` is present, it automatically dispatches the query to the active conversation.
  - Conversation Title Generation: Derived on the client side using regex heuristics in `frontend/src/services/assistantTitle.ts` (e.g., stripping "find", "search", "compare" to name the conversation). Client-side title generation will be retained in v1.4.
- **State Management & Concurrency**: `[VERIFIED FACT]`
  - Managed entirely via local component state (`useState`, `useRef`).
  - Concurrency & Race-Condition Safety: Implemented using `activeRequestRef` (request sequence counter) and `activeUserIdRef` (current authenticated user ID). When an asynchronous response returns, if the counter or user ID does not match, the response is discarded to prevent stale message pollution.
  - No global state manager (Redux/Zustand) or caching library (TanStack Query) is utilized for the assistant.

### 1.2 Backend Controllers, Services & Integrations

- **Spring Boot Controller Layer**: `[VERIFIED FACT]`
  - `AssistantController.java` (`/api/v1/assistant`):
    - **Modern APIs**: `POST /conversations`, `GET /conversations`, `GET /conversations/{id}`, `DELETE /conversations/{id}`, `POST /conversations/{id}/messages`.
    - **Legacy APIs**: `POST /chat`, `POST /compare`, `POST /ask`, `POST /clear_memory`.
- **Spring Boot Service Layer**: `[VERIFIED FACT]`
  - `ShoppingAssistantServiceImpl.java` (~1,311 lines):
    - Injected with `AiClient`, `ProductRepository`, `SearchDiscoveryService`, `QueryInterpreter`, `ComparisonService`, `PriceAnalyticsService`, `PriceWatchlistService`, `PriceAlertService`, `UserShoppingPreferenceService`, `RecommendationService`, `ShoppingIntentClassifier`, `PromptInjectionProtector`, and `DeterministicShoppingAssistantFallback`.
    - `sendMessage()` method executes a 7-step pipeline:
      1. Saves raw user message to PostgreSQL `assistant_messages`.
      2. Classifies intent using keyword heuristics (`ShoppingIntentClassifier`).
      3. Retrieves user shopping preferences (`UserShoppingPreferenceService`) and display currency.
      4. Builds an `AssistantEvidenceBundle` by querying internal services directly (search, analytics, watchlist, etc.).
      5. Calls `generateAssistantText()` -> **directly delegates to `fallbackGenerator.generateFallbackResponse(intent, bundle, preferences)`**.
      6. Serializes evidence bundle and product payload to JSON strings and saves the assistant message to PostgreSQL.
      7. Returns `AssistantResponseDTO`.
    - **Disparity Finding**: In line 1184 of `ShoppingAssistantServiceImpl.java`, `aiClient` is bypassed entirely. The comment explicitly notes: `// Pure deterministic grounded generation strictly from authoritative PricePilot evidence bundle`.
- **FastAPI AI Microservice Layer**: `[VERIFIED FACT]`
  - Located in `pricepilot-ai/app/assistant/`:
    - `router.py`: Endpoints `/assistant/chat`, `/assistant/compare`, `/assistant/ask`, `/assistant/clear_memory`. Protected by `X-API-Key` header (`verify_api_key`).
    - `orchestrator.py`: Implements `AssistantOrchestrator`. Checks `GEMINI_API_KEY` / `GOOGLE_API_KEY`. If present, calls Gemini via HTTP POST. If absent or failing, falls back to `_run_local_reasoning()`.
    - `rag.py`: Implements `RetrievalPipeline`. Inspects user query text using regex keywords, then calls Spring Boot APIs (`http://localhost:8080/api/v1/search`, `/products/{id}`, `/analytics/products/{id}`, `/price-history`, `/recommendations`) over HTTP using Python `requests.Session()`.
    - `tools.py`: Contains HTTP wrapper functions for Spring Boot REST endpoints.

---

## 2. Model Integration Audit & Operational Budgets

| Evaluation Dimension | Current Verified State | Audit Finding & Gap | Target Recommendation |
| :--- | :--- | :--- | :--- |
| **Provider & Candidate Model** | Google Gemini Developer API via direct REST `requests.post` | `[VERIFIED FACT]` In `orchestrator.py`, URL is hardcoded. `gemini-1.5-flash` is configured by default but is deprecated/shut down. | **Target Candidate: `gemini-3.8-flash`**. Must verify API availability, structured tool calling, streaming, and response-schema compatibility before implementation. Implemented behind a provider abstraction in FastAPI. |
| **Model Configuration** | Hardcoded parameters in `orchestrator.py` | `[VERIFIED FACT]` `maxOutputTokens: 2048`, `temperature: 0.2`, `topP: 0.8` hardcoded. No token counter installed. | Standardize generation configuration via environment variables with dynamic context window budgeting. |
| **Timeout Budget & Retries** | FastAPI: `timeout=10`s, 0 retries; Spring Boot `AiClientImpl`: `timeout=5000`ms, 3 retries | `[VERIFIED FACT]` Current timeout specifications are contradictory between layers. In synchronous chat, 3 retries with 5s timeout could cause a 15-20s user wait. | **Unified Timeout Budget (Locked)**:<br>• Connect timeout: 1,000 ms.<br>• Synchronous read timeout: 5,000 ms.<br>• Synchronous retries: 0 (fail fast to deterministic fallback at 5.0s max wait).<br>• Streaming read timeout: 4,000 ms (TTFT), 2,000 ms (subsequent chunks).<br>• Streaming retries: 1 retry on 429/503 with 500 ms backoff, capped at 8.0s total wait before fallback.<br>• CircuitBreaker: 50% failure rate over 10 requests trips open for 30s. |
| **CI/CD Testing Policy** | None / Unmocked | `[VERIFIED FACT]` Tests in `test_assistant.py` rely on local mocks, but lack standardized provider recordings. | **Locked Decision: Use WireMock / recorded mock fixtures for automated CI/CD**. Never require a live API key for CI. Live Gemini calls are optional in staging only. |
| **Structured Output Support** | Custom pseudo-tags parsed with regex | `[VERIFIED FACT]` Model is instructed via system prompt to output `[BUY_CONFIDENCE]` and `[COMPARISON]` tags parsed with regex in `response_formatter.py`. | Migrate to native Gemini Function Declarations and Structured Outputs (`response_schema`). |
| **Tool Calling Support** | None (Pre-RAG only) | `[VERIFIED FACT]` Gemini is called with plain prompt text. No `tools` or `function_declarations` are supplied in the request payload. | Implement native Gemini tool declarations so the model emits typed function call events back to Spring Boot. |

---

## 3. Conversation Management & Memory Retrieval Audit

### 3.1 Session Lifecycle & Multi-Turn State

- **Database-Backed State (Spring Boot - Authority)**: `[VERIFIED FACT]`
  - Persistence schema in PostgreSQL (`V1.16__create_assistant_conversations_and_messages.sql`):
    - Table `assistant_conversations`: `id`, `user_id`, `title`, `created_at`, `updated_at`.
    - Table `assistant_messages`: `id`, `conversation_id`, `role`, `content`, `intent`, `evidence_bundle`, `payload`, `created_at`.
- **Memory Buffer State (FastAPI - Volatile)**: `[VERIFIED FACT]`
  - Managed by `ConversationMemory` in `pricepilot-ai/app/assistant/memory.py`. Keyed by `email:conversation_id`, stored in a plain Python dictionary in RAM. **Pod restart or horizontal scaling wipes this memory**.
  - **Resolution**: Eliminate Python-side memory entirely. Spring Boot is the single authority for conversation state, storing all turns in PostgreSQL.

### 3.2 Memory Retrieval Query & Bidirectional Index Verification

- **Corrected SQL Retrieval Logic**: `[RECOMMENDATION]`
  To assemble the sliding context window for inference, the latest 10 messages must be retrieved in **descending timestamp order**, then **reversed in memory** to restore chronological order for prompt construction:
  ```sql
  SELECT * FROM assistant_messages 
  WHERE conversation_id = :conversationId 
  ORDER BY created_at DESC 
  LIMIT 10;
  ```
- **Index Support Verification**: `[VERIFIED FACT]`
  - Flyway migration `V1.16` contains:
    ```sql
    CREATE INDEX IF NOT EXISTS idx_assistant_msg_conv_created 
    ON assistant_messages(conversation_id, created_at ASC);
    ```
  - In PostgreSQL, standard B-tree indexes are bidirectional. The PostgreSQL query planner performs a backward index scan (`Index Scan Backward using idx_assistant_msg_conv_created`) to satisfy `ORDER BY created_at DESC LIMIT 10` without an extra sort step.
  - **Performance Claim Correction**: Query execution latency will be measured via benchmarks in staging load tests; unsubstantiated "sub-4ms" claims are formally retracted.

---

## 4. Streaming & Structured Grounding Audit

### 4.1 Latency Objectives

`[RECOMMENDATION]` **Engineering Objectives (SLOs)**:
- **Time-to-First-Token (TTFT)**: Target **< 800 ms** (measured engineering objective; benchmarked against synthetic query suite, not an unsupported guarantee).
- **Streaming Transport**: Server-Sent Events (SSE) over HTTP POST, consumed through `fetch()` and `ReadableStream`, with `AbortController` cancellation.

### 4.2 Resolving the Streaming vs. Grounding Conflict

A fundamental tension exists between progressive token streaming and post-generation grounding: streaming an unverified price token immediately to the client makes retrospective correction impossible.

`[RECOMMENDATION]` **Structured Grounding Strategy (Locked)**:
1. **Server-Generated Evidence as Sole Ground Truth**:
   - Every factual assertion originates **exclusively from server-created domain entities** produced during tool execution (`AssistantEvidenceBundle`).
   - Each evidence item is tagged with a server-generated `evidenceId`.
2. **Model Citations Are Untrusted Syntax, Not Evidence**:
   - Model-generated citations (e.g. `<citation...>` or markdown links) are parsed strictly as untrusted text formatting, **never** as verified evidence.
3. **Structured Entity Rendering in UI**:
   - Exact numerical prices, seller offers, discount badges, and inventory statuses are **rendered directly from the server-validated `evidence_bundle` SSE payload**, never parsed out of model prose.
4. **Prose Validation & Claim Buffering**:
   - The LLM streams qualitative, explanatory commentary.
   - Any token sequence that asserts a numerical price or discount is buffered by Spring Boot and verified against the server-generated `evidenceId` and its authoritative value in the active bundle before emission.
   - If a claim contradicts the authoritative evidence, the ungrounded text is suppressed or replaced with catalog data before reaching the client. **An unverified price is never streamed to the user**.

---

## 5. Domain Service & Tool Registry Audit

### 5.1 Reconciled Canonical Tool Registry (Exact 9 Tools)

All 9 tools execute exclusively through Spring Boot domain services:

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

### 5.2 Specific Tool Invariants & Integrity Boundaries

- **Tool 4 (`get_marketplace_offers`) - Freshness & Provenance Separation**: `[VERIFIED FACT]`
  - **Source Provenance (`ProvenanceType`)**: `LIVE` (real-time external merchant feed), `OFFLINE_SNAPSHOT` (persisted catalog DB record), or `SIMULATED` (offline mock adapter).
  - **Freshness & Verification Status (`VerificationStatus`)**:
    - `FRESH`: Observation age $\le$ 168 hours (604,800 seconds).
    - `STALE`: Observation age strictly $>$ 168 hours. (Preserves the exact rule from `OfferNormalizer.java`: at exactly 168h, it is not stale).
    - `UNVERIFIED`: Observation timestamp missing or unconfirmed.
  - If marketplace adapters fail, the tool returns `{ status: "MARKETPLACE_UNAVAILABLE", offers: [] }`. **Catalog baseline prices are never represented as seller offers**.
- **Tool 5 (`get_price_history`) vs Tool 6 (`get_price_forecast`) - Integrity Boundary**: `[VERIFIED FACT]`
  - `get_price_history` contains strictly observed, verified past measurements (`HistoricalPricePoint`). Zero projections.
  - `get_price_forecast` Alignment:
    - Must consume the canonical v1.4 statistical forecasting engine when available (including `modelName`, `modelVersion`, `forecastHorizonDays`, `uncertaintyInterval`, `provenance`, and `reliabilityScore`).
    - **Interim Rule (Pre-Engine)**: Until the canonical statistical forecast engine is integrated, label results strictly as **`TREND_ESTIMATE`** (calculation method: `WINDOWED_BASELINE_PERCENTAGE`)—**NOT a statistical forecast**.
    - If historical price observations < 3, **must return `trend: "INSUFFICIENT_DATA"` with `percentageChange: null`, `buyConfidenceScore: null`, `uncertaintyInterval: null`, and `reliabilityScore: null`**. It must **never** return an arbitrary default score (e.g. 70%).
- **Tools 8 & 9 (`manage_price_alert`, `manage_shopping_plan`) - Mutation & Confirmation Boundaries**: `[RECOMMENDATION]`
  - Every mutation (create alert, delete alert, create shopping plan, cancel plan) requires explicit human confirmation via a single-use, server-validated, action-scoped token.
  - Confirmation secrets are cryptographically random (256-bit entropy). Only their SHA-256 hash is stored in PostgreSQL. Raw tokens are never exposed unnecessarily in SSE payloads.
  - Background shopping plan monitoring uses **only authorized internal catalog and marketplace data sources**.

---

## 6. Security Threat Model & Risk Register

| Risk ID | Vulnerability / Threat | Affected Component | Current State & Evidence | Severity | Locked Mitigation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | **Hardcoded Password Auto-User Creation** | `AssistantController.java` (`resolveAuthenticatedUserId`) | `[VERIFIED FACT]` Lines 253-278 save a new `UserEntity` with email and password `"Password123!"` if principal is missing. | **CRITICAL** | **Remove auto-user creation immediately in AI-01**.<br>• Unauthenticated requests (missing/invalid JWT) return **`401 Unauthorized`**.<br>• Authenticated requests whose principal cannot be resolved in `users` table throw `AccessDeniedException` -> **`403 Forbidden`**.<br>• Neither case may create a user. |
| **SEC-02** | **Memory Leak in Python Inference Gateway** | `ConversationMemory` (`memory.py`) | `[VERIFIED FACT]` `self._storage` dictionary stores conversation turns in process RAM without TTL or eviction. | **HIGH** | Eliminate Python-side conversation memory. FastAPI becomes completely stateless for session history; Spring Boot passes required turn history per request. |
| **SEC-03** | **Prompt Injection Vulnerability** | `PromptInjectionProtector.java` | `[VERIFIED FACT]` Relies on 12 basic regex replacements with `[REDACTED_SECURITY_POLICY]`. | **HIGH** | XML prompt fencing is treated as supplementary formatting, **not** a security boundary. Enforce security at tool argument validation, RBAC authorization, and structured evidence verification. |
| **SEC-04** | **Unauthenticated / Insecure Memory Tampering** | `AssistantController.java` & `router.py` | `[VERIFIED FACT]` Legacy `/clear_memory` accepts arbitrary `conversationId` strings without user ownership validation. | **MEDIUM** | Enforce that all memory and conversation operations verify `conversation.getUserId().equals(authenticatedUserId)` in PostgreSQL. |
| **REL-01** | **Silent Fallback Disparity** | `ShoppingAssistantServiceImpl.java` | `[VERIFIED FACT]` Primary versioned endpoint completely bypasses LLM client and uses `DeterministicShoppingAssistantFallback`. | **HIGH** | Consolidate onto a single orchestrator path that invokes the LLM client, with fallback used strictly when the circuit breaker trips. |
| **REL-02** | **Fragile Regex-Based Grounding** | `PromptInjectionProtector.java` (`isResponseGrounded`) | `[VERIFIED FACT]` Checks price grounding via regex parsing of `$XX` strings from generated prose; bypassed in production. | **HIGH** | Redesign grounding around **structured evidence verification**: programmatic validation of server-created `evidenceId`s, product IDs, and offer provenance. |
| **REL-03** | **Fabricated Confidence on Sparse Data** | `orchestrator.py` | `[VERIFIED FACT]` Returns hardcoded 70% confidence when price history is missing. | **MEDIUM** | Return `INSUFFICIENT_DATA` with `null` confidence score whenever historical data < 3 points. |
