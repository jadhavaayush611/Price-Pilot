# AI-03 Technical Implementation Plan: SSE Streaming & Interactive Experience (Revised)

**Target Milestone**: PricePilot v1.4 (AI-03)  
**Target Services**: `frontend` (React 19), `backend` (Spring Boot 4 / Java 25), `pricepilot-ai` (FastAPI Gateway / Python 3.14)  
**Prerequisites**: AI-01 Committed (`0dee34986d722c9b230caa0d12cb348bcc1d65e7`), AI-02 Committed (`65007729c21a56a8b532ec9ed4d4eae51b46e6b8`)  
**Status**: DRAFT ARCHITECTURE PLAN (Pending User Review & Explicit Authorization)  
**Working Mode**: READ-ONLY Architectural Plan (Documentation Revision Only)  

---

## 1. Verified Facts, Design Decisions & Unresolved Risks

| Category | Item | Status |
| :--- | :--- | :--- |
| **[VERIFIED REPOSITORY FACT]** | `POST /api/v1/assistant/conversations/{id}/messages` blocks synchronously for up to 5,000 ms. | Verified in [`AssistantController.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AssistantController.java#L86) |
| **[VERIFIED REPOSITORY FACT]** | `assistant_messages` table contains `id` (PK), `conversation_id` (FK), `role`, `content`, `intent`, `evidence_bundle`, `payload`, `created_at`. No `client_message_id` column or unique index exists. | Verified in [`V1.16__create_assistant_conversations_and_messages.sql`](file:///D:/PricePilot/backend/src/main/resources/db/migration/V1.16__create_assistant_conversations_and_messages.sql#L15-L25) |
| **[VERIFIED REPOSITORY FACT]** | Browser `EventSource` cannot send custom `Authorization: Bearer <JWT>` headers or request bodies. | Standard W3C specification limit |
| **[VERIFIED REPOSITORY FACT]** | In Java 25 `HttpClient`, cancelling an already-completed `CompletableFuture` does not abort the body stream; `InputStream.close()` must be called explicitly. | Standard Java networking behavior |
| **[VERIFIED REPOSITORY FACT]** | In Starlette, synchronous streaming loops on the main event loop block asyncio execution and prevent `request.is_disconnected()` evaluation. | Standard ASGI execution behavior |
| **[PROPOSED DESIGN]** | Atomic `UpstreamStreamLifecycle` state machine with `ACTIVE`, `COMPLETED`, `CANCELLED` states and CAS transitions. | Eliminates cancellation race conditions |
| **[PROPOSED DESIGN]** | FastAPI producer-consumer architecture running provider in worker thread while asyncio event loop monitors `request.is_disconnected()`. | Ensures responsive cancellation |
| **[PROPOSED DESIGN]** | Dedicated streaming route `POST /api/v1/assistant/conversations/{id}/stream` producing `text/event-stream`. | Fully backward-compatible |
| **[PROPOSED DESIGN]** | In-flight deduplication via in-memory cache (10s TTL, HTTP 409 Conflict). Post-completion durable deduplication explicitly deferred to avoid unapproved DB migrations. | Protects database schema integrity |
| **[PROPOSED DESIGN]** | Client-safe `ClientEvidenceDTO` carrying `sourceClassification` (`LIVE`, `OFFLINE_SNAPSHOT`, `SIMULATED`) and `freshnessStatus` (`FRESH`, `STALE`, `UNVERIFIED`). | Eliminates misleading "live" claims |
| **[UNRESOLVED RISK]** | Reverse proxy / NGINX buffering SSE streams if `X-Accel-Buffering: no` is omitted. | Mitigated via response headers |
| **[UNRESOLVED RISK]** | Client disconnect leaves half-open TCP socket until next write attempt fails with `IOException`. | Mitigated via 2.0s inter-chunk inactivity timeout and `onCompletion` guard |

---

## 2. SSE Event Protocol Specification (`v1.4`)

Every event emitted over `POST /api/v1/assistant/conversations/{id}/stream` conforms to standard `text/event-stream` with strict monotonic sequence numbering and typed JSON schemas.

### 2.1 Protocol Invariants
1. **Monotonic Sequence Numbering**: Every event contains an incrementing `sequenceNumber: int` starting at 1. The client rejects out-of-order or duplicate sequence numbers.
2. **Terminal Event Exclusivity**: Exactly **ONE** terminal event (`message_complete`, `fallback`, `message_interrupted`, or `error`) is emitted per stream. Once emitted, the stream closes immediately.
3. **Identifier Enforcement**: Every event includes `conversationId` (UUID) and `messageId` (UUID).
4. **No Action Execution**: `action_proposal` is reserved for future non-mutating UI suggestions. No mutating actions or payments execute in AI-03.
5. **No Automatic Client Reconnect**: Reconnect headers specify `Cache-Control: no-cache` and `X-Accel-Buffering: no`. Automatic browser reconnect is disabled.

---

### 2.2 Event Schemas

#### 1. `message_start`
- **When**: Sent immediately after user validation and intent classification.
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 1,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "role": "assistant",
    "intent": "DISCOVERY",
    "timestamp": "2026-11-11T22:50:00.123Z"
  }
  ```

#### 2. `evidence`
- **When**: Sent immediately after grounded products, price history, and trade-offs are resolved from Spring Boot's internal catalog.
- **Payload Schema (ClientEvidenceDTO)**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 2,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "evidenceBundle": {
      "queryIntent": "DISCOVERY",
      "observationTimestamp": "2026-11-11T22:50:00.250Z",
      "provenanceSources": ["PRICEPILOT_INTERNAL_CATALOG", "SELLER_SCRAPE"],
      "groundedProducts": [
        {
          "productId": "prod-101",
          "title": "Apple MacBook Air M2",
          "brand": "Apple",
          "category": "Laptops",
          "price": 89900.0,
          "originalPrice": 99900.0,
          "discountPercent": 10.0,
          "sellerCount": 4,
          "sellerName": "Authorized Retailer",
          "sourceClassification": "OFFLINE_SNAPSHOT",
          "freshnessStatus": "FRESH",
          "observationTimestamp": "2026-11-11T22:45:00.000Z",
          "imageUrl": "https://images.pricepilot.internal/macbook.png"
        }
      ],
      "tradeOffs": ["8GB unified memory is not upgradeable"],
      "suggestedActions": []
    },
    "timestamp": "2026-11-11T22:50:00.260Z"
  }
  ```

#### 3. `text_delta`
- **When**: Emitted incrementally for every token chunk from Gemini.
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 3,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "delta": " The Apple MacBook Air M2 is currently"
  }
  ```

#### 4. `fallback` (Terminal)
- **When**: Emitted when upstream fails or times out *before* the first `text_delta`.
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 3,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "fallbackMode": true,
    "reason": "UPSTREAM_TIMEOUT",
    "response": "I analyzed the Apple MacBook Air M2. It is currently listed at ₹89,900 in our catalog snapshot.",
    "timestamp": "2026-11-11T22:50:04.500Z"
  }
  ```

#### 5. `message_interrupted` (Terminal)
- **When**: Emitted when generation halts *after* one or more `text_delta` chunks have been emitted (best-effort delivery if client is still connected).
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 28,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "reason": "USER_CANCELLATION",
    "tokensEmitted": 52,
    "timestamp": "2026-11-11T22:50:06.120Z"
  }
  ```

#### 6. `error` (Terminal)
- **When**: Emitted on fatal protocol, persistence, or authorization errors.
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 2,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "errorCode": "PERSISTENCE_FAILED",
    "message": "Assistant response could not be saved to conversation history.",
    "fatal": true,
    "timestamp": "2026-11-11T22:50:01.000Z"
  }
  ```

#### 7. `message_complete` (Terminal)
- **When**: Emitted upon successful stream completion after database persistence.
- **Payload Schema**:
  ```json
  {
    "protocolVersion": "v1.4",
    "sequenceNumber": 45,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "totalTokens": 86,
    "suggestedPrompts": [
      "Compare with Dell XPS 13",
      "Check price trend over last 30 days"
    ],
    "durationMs": 2150,
    "timestamp": "2026-11-11T22:50:02.450Z"
  }
  ```

---

## 3. Timeout Hierarchy & Authoritative Accounting

### 3.1 Timeout Breakdown & Latency Derivation

The 1,000 ms difference between Spring Boot's Time-to-First-Token (4.5s) and FastAPI's Time-to-First-Token (3.5s) represents **Pre-Call Request Preparation and Grounding Time**, not a persistence reserve.

```
[0.0s ────────────── 1.0s ────────────────────────────── 4.5s ─────── 6.5s ────────────────── 28.0s ─── 30.0s]
  │                    │                                   │            │                        │        │
  │<─ Pre-Call Prep ──>│<──── FastAPI Provider TTFT: 3.5s ─>│            │                        │        │
  │   & Grounding      │                                   │            │                        │        │
  │   (1.0s max)       │                                   │            │                        │        │
  │                                                        │<── Inter ─>│                        │        │
  │<────── Total Spring Boot TTFT Deadline: 4.5s ─────────>│    Chunk:  │                        │        │
  │                                                        │    2.0s    │                        │        │
  │                                                                                              │        │
  │<────── Maximum Stream Generation Deadline: 28.0s ───────────────────────────────────────────>│        │
  │                                                                                              │<─ Res ─>
  │                                                                                              │   2.0s
  │<────── Total SseEmitter Container Timeout: 30.0s ────────────────────────────────────────────────────>│
```

### 3.2 Authoritative Timeout Values

1. **Pre-Call Preparation & Grounding Budget**:
   - **Limit**: **1,000 ms (1.0s)**.
   - **Activities**: User JWT authentication, conversation tenancy verification, user message insert in DB, intent classification, product catalog search, price history queries, evidence bundle serialization, prompt templating, and HTTP connection setup to FastAPI.
2. **FastAPI Time-to-First-Token (TTFT)**:
   - **Limit**: **3,500 ms (3.5s)**.
   - **Measurement**: From HTTP request transmission to Gemini until first byte of first SSE `data:` candidate chunk.
   - **Action on Expiry**: Raises `ProviderTimeoutError` (504).
3. **Spring Boot Time-to-First-Token (TTFT)**:
   - **Limit**: **4,500 ms (4.5s)**.
   - **Calculation**: $1,000\text{ ms (preparation)} + 3,500\text{ ms (FastAPI)} = 4,500\text{ ms}$.
   - **Action on Expiry**: Aborts upstream stream lifecycle; invokes `DeterministicShoppingAssistantFallback`; emits `fallback` event.
4. **Inter-Chunk Inactivity Timeout**:
   - **Limit**: **2,000 ms (2.0s)**.
   - **Measurement**: Elapsed time between consecutive SSE `data:` chunk reads.
   - **Action on Expiry**: Aborts stream with `PROVIDER_TIMEOUT`; emits `message_interrupted` event.
5. **Stream Generation Deadline**:
   - **Limit**: **28,000 ms (28.0s)**.
   - **Measurement**: From client connection open to generation cutoff.
   - **Action on Expiry**: Breaks out of token loop; calls `lifecycle.cancel()`; enters 2.0s persistence reserve; persists tokens with `status: "INTERRUPTED"` and `reason: "DURATION_LIMIT_REACHED"`; emits terminal event; completes emitter cleanly before container timeout.
6. **Persistence & Finalization Reserve**:
   - **Limit**: **2,000 ms (2.0s)**.
   - **Activity**: Allocated budget for `@Transactional(timeout = 2)` database commit.
7. **`SseEmitter` Container Timeout**:
   - **Limit**: **30,000 ms (30.0s)** ($28.0\text{s} + 2.0\text{s}$).
   - **Configuration**: `new SseEmitter(30_000L)`.

---

## 4. End-to-End Cancellation Propagation Design

### 4.1 Spring Boot UpstreamStreamLifecycle Implementation

```java
package com.pricepilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class UpstreamStreamLifecycle {
    private static final Logger log = LoggerFactory.getLogger(UpstreamStreamLifecycle.class);

    public enum State {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final AtomicReference<CompletableFuture<?>> futureRef = new AtomicReference<>();
    private final AtomicReference<InputStream> streamRef = new AtomicReference<>();

    public State getState() {
        return state.get();
    }

    public void attachFuture(CompletableFuture<?> future) {
        this.futureRef.set(future);
        if (state.get() == State.CANCELLED) {
            future.cancel(true);
        }
    }

    public void attachStream(InputStream stream) {
        this.streamRef.set(stream);
        if (state.get() == State.CANCELLED) {
            closeQuietly(stream);
        }
    }

    public boolean markCompleted() {
        return state.compareAndSet(State.ACTIVE, State.COMPLETED);
    }

    public boolean cancel() {
        if (state.compareAndSet(State.ACTIVE, State.CANCELLED)) {
            CompletableFuture<?> future = futureRef.get();
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            InputStream stream = streamRef.get();
            if (stream != null) {
                closeQuietly(stream);
            }
            log.info("UpstreamStreamLifecycle: Cancelled in-flight future and closed InputStream");
            return true;
        }
        return false;
    }

    private void closeQuietly(InputStream is) {
        try {
            is.close();
        } catch (IOException ignored) {}
    }
}
```

### 4.2 FastAPI / Starlette Cancellation & Responsive Disconnect

1. **Non-Blocking Producer-Consumer**:
   - The synchronous Gemini provider runs inside a background worker thread via `loop.run_in_executor(None, worker)`.
   - Chunks are fed into an `asyncio.Queue(maxsize=32)`.
   - The ASGI endpoint on the asyncio event loop concurrently awaits:
     `queue.get()` OR `request.is_disconnected()`.
2. **Immediate Socket Preemption**:
   - When client disconnect is detected, `session.abort()` is called immediately from the event loop thread, closing the underlying OS socket handle.
   - The worker thread blocked in `conn.sock.recv()` immediately raises `OSError` / `ConnectionResetError`.
   - The worker thread catches this, unwinds, and terminates cleanly.
3. **Measured Target Cleanup Latency**:
   - Target latency upon explicit socket abort: measured target $\le 200$ ms.
   - Fallback upper bound: 2.0s inter-chunk socket timeout.
   - **Note**: Cleanup performance is treated as a measured target, not an unconditional guarantee.

---

## 5. Persistence Failure Behavior & Transaction Controls

1. **Transaction Isolation & Statement Timeout**:
   - Partial message persistence is executed within `@Transactional(timeout = 2)`.
2. **Persistence Failure Semantics**:
   - If the database commit times out or throws `DataAccessException`:
     - The JPA transaction rolls back.
     - An ERROR is logged containing conversation ID and emitted token count.
     - The partial message is **not** committed to PostgreSQL.
     - If the client SSE connection is still writable, Spring Boot sends an `error` SSE event with `errorCode: "PERSISTENCE_FAILED"`.
     - Emitter is completed.
3. **Separation of Commit vs. Delivery Guarantees**:
   - Database persistence success and client SSE delivery success are separate failure domains and are treated independently.

---

## 6. Evidence & Numerical Grounding Boundary

1. **Accurate Provenance & Freshness**:
   - Grounded products carry `sourceClassification: LIVE | OFFLINE_SNAPSHOT | SIMULATED` and `freshnessStatus: FRESH | STALE | UNVERIFIED`.
   - Verified data comes strictly from Spring Boot's internal catalog and pricing services.
2. **UI Presentation & Contradiction Rule**:
   - Product cards display actual source labels (e.g. `Catalog Snapshot (2h ago)` or `Demo Catalog Price`).
   - Grounded product cards are the **sole authoritative source** for prices and availability.
   - Conversational text below the cards is qualified with an explicit banner:
     *"Prices and availability shown in product cards above represent PricePilot [SourceClassification] catalog data ([FreshnessStatus]). Any numerical estimates in the conversational text below are AI-generated suggestions and not guaranteed."*

---

## 7. Idempotency & Deduplication Strategy

1. **Client Message Identifier (`clientMessageId`)**:
   - React client passes a UUID `clientMessageId` in `SendMessageRequest`.
2. **In-Flight Concurrency Deduplication**:
   - Spring Boot maintains an in-memory cache (10s sliding TTL):
     `Key: (conversationId, clientMessageId) -> ActiveStreamContext`.
   - If a duplicate POST arrives while generation is `ACTIVE`, returns HTTP `409 Conflict` with `STREAM_IN_PROGRESS`.
3. **Post-Completion Durable Deduplication Explicitly Deferred**:
   - Because `assistant_messages` lacks a `client_message_id` column and unique constraint, post-completion durable idempotency is **explicitly deferred** to avoid unapproved schema migrations.
4. **No Stream Reconnection / Replay**:
   - Resuming dropped streams is not supported in AI-03; client must resubmit.

---

## 8. Frontend Stream Parser Specification

### 8.1 Stream Parser Implementation (`frontend/src/services/streamParser.ts`)
1. **UTF-8 Multi-Byte Streaming**:
   `new TextDecoder("utf-8", { fatal: false })` with `{ stream: true }` prevents corrupting multi-byte sequences that span chunk boundaries.
2. **Line Normalization**:
   Splits on both `\r\n` and `\n`.
3. **Multiline `data:` Concatenation**:
   Joins multiple consecutive `data:` lines within the same frame with `\n`.
4. **Buffer Residue on Stream EOF**:
   Flushes trailing decoder bytes; if trailing frame is complete, emits it; if incomplete, synthesizes `message_interrupted` with `reason: "UNEXPECTED_EOF"`.
5. **Frame Validation & Error Handling**:
   - Malformed JSON: Discards corrupted frame, logs warning, continues reading stream.
   - Unknown event types: Ignored per W3C SSE specification.
   - Duplicate or out-of-order sequence numbers: Discards frame to prevent repeated text rendering.
   - Missing sequence numbers: Flagged as unsequenced.
6. **Controlled UI Failure State**:
   Displays an error banner with a `Retry` action instead of crashing the React component tree.
7. **Performance Targets vs. Guarantees**:
   TTFT $\le 1,000$ ms and evidence render $\le 500$ ms are **measured performance targets**, not guaranteed acceptance criteria.

---

## 9. Phased Implementation Sequence

```
Phase 1: Provider Layer Streaming (FastAPI contracts, base, gemini, mock)
Phase 2: FastAPI Streaming Router (POST /assistant/chat/stream with producer-consumer & socket teardown)
Phase 3: Spring Boot Streaming Client & Service (AiClient stream, UpstreamStreamLifecycle, SseEmitter)
Phase 4: Spring Boot Streaming Controller (POST /conversations/{id}/stream with in-flight deduplication)
Phase 5: React Streaming Client & UI Integration (fetch ReadableStream, Stop button, smart autoscroll)
Phase 6: End-to-End Offline Test Verification & Packaging Build
```

---

## 10. Exact Files Expected to Change

### Backend (Spring Boot)
- [`backend/src/main/java/com/pricepilot/ai/AssistantController.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AssistantController.java): Add streaming endpoint.
- [`backend/src/main/java/com/pricepilot/ai/AiClient.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AiClient.java): Add streaming client method.
- [`backend/src/main/java/com/pricepilot/ai/AiClientImpl.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AiClientImpl.java): Implement streaming HTTP exchange with `UpstreamStreamLifecycle`.
- [`backend/src/main/java/com/pricepilot/intelligence/assistant/service/ShoppingAssistantService.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/intelligence/assistant/service/ShoppingAssistantService.java): Add `streamMessage` signature.
- [`backend/src/main/java/com/pricepilot/intelligence/assistant/service/ShoppingAssistantServiceImpl.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/intelligence/assistant/service/ShoppingAssistantServiceImpl.java): Implement streaming lifecycle, evidence emission, and fallback.
- `backend/src/main/java/com/pricepilot/ai/UpstreamStreamLifecycle.java` (New): Atomic three-state lifecycle state machine.
- `backend/src/main/java/com/pricepilot/intelligence/assistant/dto/AssistantStreamEvent.java` (New): Typed SSE event DTOs.
- `backend/src/main/java/com/pricepilot/intelligence/assistant/dto/ClientEvidenceDTO.java` (New): Client-safe evidence DTO with provenance.
- `backend/src/test/java/com/pricepilot/intelligence/assistant/AssistantStreamingIntegrationTest.java` (New): Integration test suite.

### AI Microservice (FastAPI)
- [`pricepilot-ai/app/assistant/providers/contracts.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/providers/contracts.py): Add `LlmStreamChunk`.
- [`pricepilot-ai/app/assistant/providers/base.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/providers/base.py): Add `generate_text_stream`.
- [`pricepilot-ai/app/assistant/providers/gemini.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/providers/gemini.py): Implement SSE streaming from Gemini with socket registration.
- [`pricepilot-ai/app/assistant/providers/mock.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/providers/mock.py): Implement offline mock streaming.
- [`pricepilot-ai/app/assistant/orchestrator.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/orchestrator.py): Add `chat_stream`.
- [`pricepilot-ai/app/assistant/router.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/router.py): Add `POST /assistant/chat/stream` with ASGI disconnect detection.
- `pricepilot-ai/tests/test_streaming_provider.py` (New): Provider streaming and cancellation test suite with local TCP server.

### Frontend (React)
- [`frontend/src/services/api.ts`](file:///D:/PricePilot/frontend/src/services/api.ts): Add `streamAssistantMessage`.
- `frontend/src/services/streamParser.ts` (New): W3C-compliant SSE parser with UTF-8 decoding.
- [`frontend/src/pages/AiAssistantPage.tsx`](file:///D:/PricePilot/frontend/src/pages/AiAssistantPage.tsx): Implement streaming buffer, Stop button, and autoscroll.

---

## 11. Testing & Verification Strategy (100% Offline)

1. **Provider Streaming & Cancellation Tests (`test_streaming_provider.py`)**:
   - Local TCP socket server emitting simulated Gemini SSE lines.
   - Verifies chunk emission, monotonic sequence mapping, and `conn.close()` socket teardown within the measured target window ($\le 2.0\text{s}$) upon client abort.
   - Verifies absence of leaked worker threads.
2. **Spring Boot Integration Tests (`AssistantStreamingIntegrationTest.java`)**:
   - Tests `POST /api/v1/assistant/conversations/{id}/stream` with `MockMvc`.
   - Asserts sequence: `message_start` (1) $\to$ `evidence` (2) $\to$ `text_delta` (3) $\to$ `message_complete` (4).
   - Pre-token timeout test asserting `fallback` event emission.
   - Post-token disconnect test asserting `message_interrupted` and database status `INTERRUPTED`.
3. **Frontend Tests (`AiAssistantPage.test.tsx`)**:
   - Tests buffer reassembly across fragmented byte slices, CRLF/LF line endings, and multiline `data:` frames.
   - Tests user clicking "Stop" triggering abort signal.
4. **Regression Verification**:
   - All 34 FastAPI tests pass.
   - All 864 Spring Boot backend tests pass.
   - Production packaging builds without errors.

---

## 12. Acceptance Criteria

1. `POST /api/v1/assistant/conversations/{id}/stream` delivers real-time SSE events with valid headers (`Content-Type: text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`).
2. Verified product evidence and first text token meet measured latency targets ($\le 500$ ms and $\le 1,000$ ms).
3. Pre-token provider failures cleanly emit `fallback` with deterministic text.
4. Post-token failures or cancellations emit `message_interrupted` without appending fallback text.
5. Partial messages persist cleanly in `content` with status metadata in `payload`.
6. Client abort immediately halts upstream Gemini API socket generation within measured target window.
7. Zero database migrations; zero external dependencies; protected ambient files untouched.
8. Repository remains on `develop`.
