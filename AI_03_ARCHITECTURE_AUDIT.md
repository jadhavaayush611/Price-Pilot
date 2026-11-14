# AI-03 Architecture Audit: SSE Streaming & Interactive Experience (Revised)

**Milestone**: PricePilot v1.4 (AI-03)  
**Target Capabilities**: Server-Sent Events (SSE) Streaming, End-to-End Cancellation, Real-Time Grounded Evidence Delivery, Interactive Stop-Generation UI  
**Prerequisites**: AI-01 Committed (`0dee34986d722c9b230caa0d12cb348bcc1d65e7`), AI-02 Committed (`65007729c21a56a8b532ec9ed4d4eae51b46e6b8`)  
**Audit Date**: November 2026 (Final Pre-Implementation Correctness Revision)  
**Auditor**: Antigravity AI Engineering  
**Working Mode**: READ-ONLY Architectural Audit (Documentation Revision Only)  

---

## 1. Verified Repository Facts vs. Proposed Design Decisions vs. Unresolved Risks

To maintain absolute architectural integrity, all statements in this audit are explicitly partitioned into three categories:

| Category | Definition | Status |
| :--- | :--- | :--- |
| **[VERIFIED REPOSITORY FACT]** | Directly verified against checked-in source code, test execution, schema migrations, or configuration. | Guaranteed in current repository |
| **[PROPOSED DESIGN]** | Recommended architecture, protocol schemas, and concurrency controls designed for AI-03. | Proposed design (not yet implemented) |
| **[UNRESOLVED RISK]** | Edge cases, network limits, or upstream behaviors requiring explicit boundary handling. | Documented & mitigated |

---

## 2. Executive Summary & Verification Findings

1. **[VERIFIED REPOSITORY FACT] Synchronous Architecture**: The existing assistant implementation ([`POST /api/v1/assistant/conversations/{id}/messages`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AssistantController.java#L86)) blocks synchronously. Spring Boot gathers grounding evidence, calls FastAPI (`POST /assistant/chat`) with a 5,000 ms total deadline, waits for the full Gemini response, saves the message, and returns an `AssistantResponseDTO`.
2. **[PROPOSED DESIGN] Dedicated Streaming Endpoint**: Introduce `POST /api/v1/assistant/conversations/{id}/stream` producing `text/event-stream`. The existing synchronous endpoint remains 100% untouched for backward compatibility.
3. **[VERIFIED REPOSITORY FACT] Authentication Transport Constraint**: Browser `EventSource` API does not support HTTP `POST` or custom authorization headers (e.g. `Authorization: Bearer <JWT>`). Because PricePilot assistant conversations are authenticated via JWT bearer tokens and require a JSON body (`SendMessageRequest`), the React client must consume SSE using standard `fetch()` and `ReadableStream`.
4. **[VERIFIED REPOSITORY FACT] Separation of Concerns**:
   - **Spring Boot**: Sole owner of user authentication, tenant isolation, conversation state, message persistence, intent classification, catalog grounding, and deterministic fallback.
   - **FastAPI**: Stateless inference gateway adapting Gemini's upstream SSE tokens into standardized stream events. FastAPI does **not** have database access.
5. **[PROPOSED DESIGN] Strict Fallback & Interruption Boundary**:
   - *Before any text delta is emitted*: Deterministic fallback is permitted if the provider fails or times out.
   - *After any text delta is emitted*: Deterministic fallback is **strictly prohibited**. The stream terminates with an explicit `message_interrupted` event, and the partial response is persisted with a distinct status metadata marker.
6. **[VERIFIED REPOSITORY FACT] Database Schema & Idempotency Limit**:
   - Inspected `assistant_messages` table ([`V1.16__create_assistant_conversations_and_messages.sql`](file:///D:/PricePilot/backend/src/main/resources/db/migration/V1.16__create_assistant_conversations_and_messages.sql#L15-L25)).
   - Columns: `id` (UUID PK), `conversation_id` (UUID FK), `role` (VARCHAR), `content` (TEXT), `intent` (VARCHAR), `evidence_bundle` (TEXT), `payload` (TEXT), `created_at` (TIMESTAMP).
   - There is **no dedicated `client_message_id` column** and **no unique constraint on `(conversation_id, client_message_id)`**.
   - Therefore, post-completion durable idempotency is **explicitly deferred** to a future milestone to avoid unapproved schema migrations. AI-03 provides **in-flight concurrency deduplication only** via an in-memory cache.

---

## 3. End-to-End Request Lifecycle & Transport Capabilities

### 3.1 Architecture Overview & Sequence Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as Shopper (React Browser)
    participant SB as Spring Boot Orchestrator (/assistant/conversations/{id}/stream)
    participant DB as PostgreSQL (assistant_messages)
    participant FA as FastAPI Gateway (/assistant/chat/stream)
    participant GEM as Gemini Developer API (streamGenerateContent?alt=sse)

    User->>SB: POST /api/v1/assistant/conversations/{id}/stream (JWT, SendMessageRequest)
    Note over SB: [VERIFIED] Authenticate user & verify conversation ownership
    SB->>DB: Save user message (role: USER)
    Note over SB: [VERIFIED] Classify intent & assemble grounded evidence bundle

    SB-->>User: SSE event: "message_start" { messageId, intent, sequenceNumber: 1 }
    SB-->>User: SSE event: "evidence" { ClientEvidenceDTO, sequenceNumber: 2 }

    SB->>FA: POST /assistant/chat/stream (LlmCompletionRequest)
    FA->>GEM: POST /v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse

    loop Incremental Token Delivery
        GEM-->>FA: data: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
        FA-->>SB: data: {"text_delta": "..."}
        SB-->>User: SSE event: "text_delta" { delta: "...", sequenceNumber: n }
        Note over User: Append token to live buffer; autoscroll if near bottom
    end

    alt Stream Completed Normally
        GEM-->>FA: data: {"finishReason": "STOP", "usageMetadata": {...}}
        FA-->>SB: data: {"is_terminal": true, "finish_reason": "STOP"}
        Note over SB: Generation loop calls lifecycle.markCompleted()
        SB->>DB: Save assistant message (content, status: COMPLETED)
        SB-->>User: SSE event: "message_complete" { totalTokens, suggestedPrompts }
        SB-->>User: [Close SSE Connection via emitter.complete()]
    else User Clicks "Stop Generating" / Disconnect
        User->>SB: Client Aborts HTTP Connection (fetch abort)
        Note over SB: SseEmitter detects disconnect -> lifecycle.cancel()
        SB->>FA: Close upstream HTTP stream (InputStream.close())
        Note over FA: ASGI disconnect detected -> ProviderStreamSession.abort()
        FA->>GEM: conn.close() (Immediate OS socket closure)
        SB->>DB: Save partial message (content, status: INTERRUPTED in payload)
    else Upstream Times Out before First Token (TTFT > 3.5s)
        FA-->>SB: Error / Timeout (504)
        Note over SB: Trigger DeterministicShoppingAssistantFallback
        SB->>DB: Save fallback message (fallbackMode: true)
        SB-->>User: SSE event: "fallback" { fallbackMode: true, response: "..." }
        SB-->>User: [Close SSE Connection]
    end
```

---

### 3.2 Spring Boot Cancellation State Machine & Lifecycle Management

#### [VERIFIED REPOSITORY FACT] The Future vs. Stream Cancellation Gap
1. In [`AiClientImpl.java`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AiClientImpl.java#L47-L53), `HttpClient.sendAsync(..., BodyHandlers.ofInputStream())` completes its `CompletableFuture<HttpResponse<InputStream>>` as soon as the HTTP status and headers are parsed.
2. Cancelling an already-completed `CompletableFuture` has **zero effect** on the active HTTP response body stream.
3. The underlying network connection remains active and the thread continues reading until the returned `InputStream` is explicitly closed via `inputStream.close()`.

#### [PROPOSED DESIGN] Atomic Three-State Lifecycle State Machine
To eliminate race conditions between separate boolean flags, the stream lifecycle is governed by a single atomic state machine with mutually exclusive compare-and-set transitions:

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

    /**
     * Attaches the in-flight upstream CompletableFuture.
     * If the lifecycle is ALREADY in CANCELLED state when attached,
     * the future is cancelled immediately.
     */
    public void attachFuture(CompletableFuture<?> future) {
        this.futureRef.set(future);
        if (state.get() == State.CANCELLED) {
            future.cancel(true);
        }
    }

    /**
     * Attaches the active HTTP InputStream once headers arrive.
     * If the lifecycle is ALREADY in CANCELLED state when attached,
     * the InputStream is closed immediately.
     */
    public void attachStream(InputStream inputStream) {
        this.streamRef.set(inputStream);
        if (state.get() == State.CANCELLED) {
            closeQuietly(inputStream);
        }
    }

    /**
     * Called EXPLICITLY by the server-side generation loop upon normal stream completion,
     * BEFORE calling emitter.complete().
     * Returns true if transition from ACTIVE -> COMPLETED succeeded.
     */
    public boolean markCompleted() {
        return state.compareAndSet(State.ACTIVE, State.COMPLETED);
    }

    /**
     * Idempotently cancels upstream operations.
     * Mutually exclusive with markCompleted():
     * If already COMPLETED, returns false (no cancellation).
     * If ACTIVE, transitions to CANCELLED and closes both future and stream.
     */
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
            log.info("UpstreamStreamLifecycle: Transitioned to CANCELLED and aborted active resources");
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

#### [PROPOSED DESIGN] SseEmitter Callback Logic & Normal vs. Disconnect Differentiation
In Tomcat and Spring MVC, `emitter.onCompletion(...)` is invoked when the async request completes, regardless of whether it completed normally or because of a client disconnect.

To reliably distinguish normal server completion from client disconnect:
1. **Normal Server Completion**:
   The server-side token generation loop explicitly calls `lifecycle.markCompleted()` immediately after receiving the terminal token and before calling `emitter.complete()`. The state transitions `ACTIVE -> COMPLETED`.
2. **Abnormal Disconnect / Container Teardown**:
   If `emitter.onCompletion(...)` fires while `lifecycle.getState() == State.ACTIVE` (meaning the server generation loop did not mark it completed), the completion was triggered by an external client drop or container timeout.
3. **Idempotent Callbacks**:
   ```java
   SseEmitter emitter = new SseEmitter(30_000L); // 30.0s container timeout
   UpstreamStreamLifecycle lifecycle = new UpstreamStreamLifecycle();

   // Generation loop execution (in background task executor):
   try {
       // ... consume upstream tokens ...
       lifecycle.markCompleted(); // Transitions ACTIVE -> COMPLETED
       emitter.complete();
   } catch (Exception ex) {
       lifecycle.cancel(); // Transitions ACTIVE -> CANCELLED
   }

   // Emitter callbacks:
   emitter.onCompletion(() -> {
       // If state is still ACTIVE, the server loop did NOT complete normally!
       if (lifecycle.getState() == UpstreamStreamLifecycle.State.ACTIVE) {
           lifecycle.cancel();
       }
   });

   emitter.onTimeout(() -> {
       log.warn("SseEmitter timed out for conversation {}", conversationId);
       lifecycle.cancel();
       emitter.complete();
   });

   emitter.onError(ex -> {
       log.debug("SseEmitter encountered error: {}", ex.getMessage());
       lifecycle.cancel();
   });
   ```

#### [PROPOSED DESIGN] Bounded Executor & Overload Protection
1. **Dedicated TaskExecutor**:
   - `corePoolSize = 10`, `maxPoolSize = 50`, `queueCapacity = 100`.
   - Rejection policy: `ThreadPoolExecutor.AbortPolicy`.
2. **Overload Rejection**:
   When the queue is saturated, Spring Boot rejects new streaming requests with:
   - HTTP Status: `503 Service Unavailable`
   - Header: `Retry-After: 5`
   - JSON Body: `{ "errorCode": "STREAM_CAPACITY_EXCEEDED", "message": "Assistant stream capacity reached. Please retry in a few seconds." }`

---

### 3.3 FastAPI / Starlette Cancellation & Responsive Disconnect Monitoring

#### [VERIFIED REPOSITORY FACT] The Async Event Loop & Synchronous Generator Hazard
1. In FastAPI / Starlette, running a synchronous generator that performs blocking socket reads inside an `async for` loop on the main asyncio event loop **blocks the event loop thread**.
2. While the event loop is blocked in a synchronous read, it cannot evaluate `await request.is_disconnected()`, nor can it process incoming ASGI disconnect packets!
3. Furthermore, AnyIO task cancellation cannot preempt a native blocking `recv()` call inside a worker thread.

#### [PROPOSED DESIGN] Concurrent Disconnect Monitoring & Thread-Safe Socket Teardown
To ensure responsive disconnect detection without blocking the event loop:
1. **`ProviderStreamSession`**:
   Holds a thread-safe reference to the active `http.client.HTTPSConnection`.
2. **Producer-Consumer Architecture**:
   - A background worker thread executes the synchronous Gemini provider streaming call and pushes chunks into an `asyncio.Queue`.
   - The ASGI endpoint on the asyncio event loop concurrently monitors two async tasks:
     Task A: `queue.get()` (waiting for next chunk).
     Task B: `request.is_disconnected()` (waiting for client disconnect).
3. **Responsive Interruption Flow**:
   ```python
   class ProviderStreamSession:
       def __init__(self):
           self.conn: Optional[http.client.HTTPSConnection] = None
           self._aborted = False

       def register_conn(self, conn: http.client.HTTPSConnection):
           self.conn = conn
           if self._aborted:
               self._close_socket()

       def abort(self):
           self._aborted = True
           self._close_socket()

       def _close_socket(self):
           if self.conn:
               try:
                   self.conn.close()
               except Exception:
                   pass

   async def stream_chat_endpoint(request: Request, body: LlmCompletionRequest):
       session = ProviderStreamSession()
       queue: asyncio.Queue = asyncio.Queue(maxsize=32)
       loop = asyncio.get_running_loop()

       # Worker thread performs synchronous provider generation
       def worker():
           try:
               for chunk in orchestrator.chat_stream(body, session):
                   if session._aborted:
                       break
                   asyncio.run_coroutine_threadsafe(queue.put(chunk), loop).result()
           except Exception as e:
               asyncio.run_coroutine_threadsafe(queue.put(e), loop).result()
           finally:
               asyncio.run_coroutine_threadsafe(queue.put(None), loop).result()

       worker_future = loop.run_in_executor(None, worker)

       async def event_publisher():
           try:
               while True:
                   get_task = asyncio.create_task(queue.get())
                   disconnect_task = asyncio.create_task(request.is_disconnected())

                   done, pending = await asyncio.wait(
                       [get_task, disconnect_task],
                       return_when=asyncio.FIRST_COMPLETED
                   )
                   for t in pending:
                       t.cancel()

                   if disconnect_task in done and disconnect_task.result():
                       logger.info("ASGI disconnect detected; aborting provider session")
                       session.abort()  # Forcibly closes HTTPSConnection socket from event loop!
                       break

                   if get_task in done:
                       item = get_task.result()
                       if item is None:
                           break  # Normal stream EOF
                       if isinstance(item, Exception):
                           raise item
                       yield f"data: {item.model_dump_json()}\n\n"
           finally:
               session.abort()

       return StreamingResponse(event_publisher(), media_type="text/event-stream")
   ```
4. **Immediate Preemption Mechanism**:
   - Calling `session.abort()` from the event loop thread closes the underlying OS socket handle.
   - The worker thread blocked in `conn.sock.recv()` immediately raises `OSError` / `ConnectionResetError`.
   - The worker thread catches this, exits its loop, and terminates.
5. **Measured Target Cleanup Latency**:
   - Explicit socket closure target latency: **$\le 200$ ms** upon disconnect detection.
   - Fallback upper bound: **2.0 seconds** (the inter-chunk socket timeout) if OS socket notification is delayed.
   - **Note**: This cleanup latency is a measured and tested performance target, not an unmeasured guarantee.

#### [PROPOSED DESIGN] Automated Offline Integration Test
In `tests/test_streaming_provider.py`:
- Spin up a local `socketserver.TCPServer` that sends one simulated SSE chunk and then intentionally stalls without closing.
- Connect via `httpx.AsyncClient` through the FastAPI app.
- Receive the first chunk, then immediately close the client connection.
- Assert that:
  1) The local TCP server detects socket closure within the measured target window ($\le 2,000$ ms).
  2) The background worker thread terminates cleanly.
  3) No worker threads leak in the test process.

---

## 4. Latency Budgets & Streaming Timeout Hierarchy

### 4.1 Accurate Timeout Breakdown & Derivation

The 1,000 ms difference between Spring Boot's Time-to-First-Token (4.5s) and FastAPI's Time-to-First-Token (3.5s) is **Pre-Call Request Preparation and Grounding Time**, not a persistence reserve.

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

### 4.2 Authoritative Timeout Values

| Timeout Component | Authoritative Budget | Owning Service / Boundary | Concrete Activities Included | Action Upon Expiration |
| :--- | :--- | :--- | :--- | :--- |
| **Pre-Call Preparation & Grounding** | **1,000 ms** (max) | Spring Boot (`ShoppingAssistantService`) | User JWT auth, conversation ownership check, user message DB insert, intent classification, internal catalog product query, price history query, prompt assembly. | Fail fast before calling LLM. |
| **FastAPI Provider TTFT** | **3,500 ms** | FastAPI / `GeminiProvider` | Request serialization, DNS, TLS connect to Gemini, upstream LLM queue/scheduling, arrival of first SSE candidate byte. | Raises `ProviderTimeoutError` (504); terminates upstream socket. |
| **Spring Boot Total TTFT** | **4,500 ms** | Spring Boot / `AiClient.streamChat` | Combined Preparation (1.0s) + FastAPI TTFT (3.5s). Measured from client request receipt to arrival of first `text_delta`. | Aborts upstream future; invokes `DeterministicShoppingAssistantFallback`; emits `fallback` SSE event. |
| **Inter-Chunk Inactivity** | **2,000 ms** | Both Spring Boot and FastAPI | Idle time between consecutive SSE `data:` chunk arrivals. | Aborts stream; marks as `PROVIDER_TIMEOUT`; emits `message_interrupted` event. |
| **Stream Generation Deadline** | **28,000 ms** (28.0s) | Spring Boot stream loop | Active token consumption and delivery loop. | Stops stream consumption; aborts upstream generator; enters 2.0s persistence reserve. |
| **Persistence & Finalization Reserve** | **2,000 ms** (2.0s) | Spring Boot database transaction | Committing partial tokens to `assistant_messages`, writing terminal metadata. | Budgeted reserve for JPA transaction commit. |
| **SseEmitter Container Timeout** | **30,000 ms** (30.0s) | Spring Boot `SseEmitter` | Total HTTP response lifecycle ($28.0\text{s}\text{ generation} + 2.0\text{s}\text{ reserve}$). | Closes emitter cleanly. If generation deadline fired at 28.0s, emitter completes normally at ~28.5s without timing out. |

---

## 5. Persistence Failure Behavior & Transaction Controls

### 5.1 [VERIFIED REPOSITORY FACT] Transaction Configuration in Stack
1. In `application-dev.properties` ([`application-dev.properties`](file:///D:/PricePilot/backend/src/main/resources/application-dev.properties#L9-L16)), HikariCP is configured with `connection-timeout=20000` (20s).
2. Spring JPA supports `@Transactional(timeout = 2)` (2-second transaction timeout).
3. PostgreSQL JDBC driver supports `statement_timeout`.

### 5.2 [PROPOSED DESIGN] Persistence Failure Semantics
The 2.0-second persistence reserve is an allocated **latency budget**, not a mathematical guarantee that database persistence will succeed (e.g., database network partition, pool exhaustion, or deadlocks can occur).

1. **Transaction Isolation & Timeout**:
   - Partial message persistence runs in a dedicated transaction with `@Transactional(timeout = 2)`.
2. **Persistence Failure Handling**:
   - If the database commit times out or throws a `DataAccessException`:
     - The JPA transaction rolls back.
     - An ERROR is logged containing conversation ID and emitted token count.
     - The partial message is **not** committed to PostgreSQL.
     - If the client SSE connection is still writable, Spring Boot sends an `error` SSE event:
       ```json
       {
         "protocolVersion": "v1.4",
         "sequenceNumber": 99,
         "conversationId": "...",
         "messageId": "...",
         "errorCode": "PERSISTENCE_FAILED",
         "message": "Assistant response could not be saved to conversation history.",
         "fatal": true,
         "timestamp": "2026-11-11T22:50:28.500Z"
       }
       ```
     - Emitter is completed.
3. **Separation of Commit vs. Delivery Guarantees**:
   - **Database Persistence** and **Client SSE Delivery** are distinct and can fail independently:
     - *Client drops, DB commit succeeds*: Partial message is saved with status `INTERRUPTED`; user sees it when reloading history.
     - *Client connected, DB commit fails*: Client receives `PERSISTENCE_FAILED` error event; message is not in history.
     - *Both fail*: Logged on server; no dangling state.

---

## 6. Marketplace Provenance & Numerical Grounding Boundary

### 6.1 Accurate Provenance & Freshness Classification

To avoid false or misleading claims, PricePilot does **not** make blanket claims of "verified live" pricing. Catalog data may come from real-time seller scraping, scheduled batch snapshots, or internal simulated seeds.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          ClientEvidenceDTO                             │
├────────────────────────────────────────────────────────────────────────┤
│ • observationTimestamp: Instant                                        │
│ • provenanceSources: ["PRICEPILOT_INTERNAL_CATALOG", "SELLER_SCRAPE"]  │
│ • groundedProducts: List<GroundedProductItem>                          │
│     ├── productId: String                                              │
│     ├── title: String                                                  │
│     ├── brand: String                                                  │
│     ├── price: BigDecimal                                              │
│     ├── originalPrice: BigDecimal                                      │
│     ├── discountPercent: Double                                        │
│     ├── sellerCount: Integer                                           │
│     ├── sourceClassification: LIVE | OFFLINE_SNAPSHOT | SIMULATED      │
│     └── freshnessStatus: FRESH | STALE | UNVERIFIED                    │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Source Classifications**:
   - `LIVE`: Live real-time scrape or direct partner API call completed within the last 15 minutes.
   - `OFFLINE_SNAPSHOT`: Periodic database snapshot cached within the past 24 hours.
   - `SIMULATED`: Internal mock/demo product catalog dataset.
2. **Freshness Statuses**:
   - `FRESH`: Verified within current freshness threshold.
   - `STALE`: Observation timestamp exceeds 24 hours.
   - `UNVERIFIED`: Estimated or unconfirmed pricing.

### 6.2 UI Rendering & Numerical Grounding Rules
1. **Product Cards**:
   - Render labels based on actual evidence fields (e.g. `Live Retailer Price`, `Catalog Snapshot (2h ago)`, or `Demo Catalog Price`).
   - Grounded product cards are the **sole authoritative source** for critical prices, discounts, and availability.
2. **Conversational LLM Prose**:
   - Generated prose text is treated as unverified shopping commentary.
   - The UI displays an explicit qualification banner:
     *"Prices and availability shown in product cards above represent PricePilot [SourceClassification] catalog data ([FreshnessStatus]). Any numerical estimates in the conversational text below are AI-generated suggestions and not guaranteed."*
   - In the event of a numerical contradiction between generated text and a product card, the product card is the authoritative source.

---

## 7. Idempotency & Deduplication Strategy

### 7.1 [VERIFIED REPOSITORY FACT] Database Schema Limitations
- Inspected [`V1.16__create_assistant_conversations_and_messages.sql`](file:///D:/PricePilot/backend/src/main/resources/db/migration/V1.16__create_assistant_conversations_and_messages.sql#L15-L25).
- `assistant_messages` table contains `id` (PK), `conversation_id` (FK), `role`, `content`, `intent`, `evidence_bundle`, `payload`, `created_at`.
- There is **no `client_message_id` column** and **no unique constraint on `(conversation_id, client_message_id)`**.
- Querying unindexed serialized JSON in `payload` with `LIKE` or regex is slow, unindexed, and provides no transactional concurrency protection against simultaneous inserts.

### 7.2 [PROPOSED DESIGN] In-Flight Deduplication (Post-Completion Deferred)
1. **Client Identifier (`clientMessageId`)**:
   - React client passes a UUID `clientMessageId` in `SendMessageRequest`.
2. **In-Flight Concurrency Deduplication**:
   - Spring Boot maintains an in-memory sliding-window cache (Caffeine cache, 10-second TTL):
     `Key: (conversationId, clientMessageId) -> ActiveStreamContext`.
   - If a duplicate POST arrives with the same `(conversationId, clientMessageId)` while an existing stream is `ACTIVE`:
     - Spring Boot immediately rejects the request with HTTP `409 Conflict`:
       ```json
       {
         "errorCode": "STREAM_IN_PROGRESS",
         "message": "A stream for this message is currently in progress."
       }
       ```
   - This prevents rapid double-clicks from spawning duplicate user messages or parallel LLM streams.
3. **Post-Completion Deduplication Explicitly Deferred**:
   - Durable post-completion idempotency across server restarts is **explicitly deferred** to a future milestone when a dedicated schema migration adding `client_message_id` with a database-enforced unique constraint can be introduced and justified.
   - AI-03 does **not** introduce unapproved database migrations.
4. **No Stream Reconnection / Replay**:
   - AI-03 does **not** support stream reattachment, offset replay, or resumption of dropped connections.
   - Dropped streams must be resubmitted as a new conversational turn.

---

## 8. Frontend Stream Parser & UI Resilience

### 8.1 Stream Parser Robustness (`streamParser.ts`)
1. **UTF-8 Chunk Boundaries**:
   - Uses `new TextDecoder("utf-8", { fatal: false })` with `{ stream: true }`. Multi-byte UTF-8 sequences split across TCP chunk boundaries are retained in the decoder's internal state without emitting `\uFFFD`.
2. **Line Ending Agnosticism**:
   - Normalizes incoming stream chunks on both `\r\n` (CRLF) and `\n` (LF).
3. **Multiline `data:` Concatenation**:
   - Joins multiple consecutive `data:` lines within the same event frame with `\n` per W3C specification.
4. **Buffer Residue on Stream EOF**:
   - When `reader.read()` returns `{ done: true }`:
     - Flushes the final decoder chunk with `decoder.decode()`.
     - If the trailing buffer contains a complete frame, parses and emits it.
     - If the trailing buffer is non-empty but incomplete, discards corrupted bytes and synthesizes `message_interrupted` with `reason: "UNEXPECTED_EOF"`.
5. **Frame Validation & Error Handling**:
   - **Malformed JSON**: Logs warning, discards corrupted frame, does not terminate parser.
   - **Unknown Event Types**: Safely ignored per W3C SSE specification.
   - **Duplicate / Out-of-Order Sequences**: If `sequenceNumber <= lastSeenSequenceNumber`, frame is discarded to prevent duplicate text rendering.
   - **Missing Sequence Numbers**: Flagged and logged as unsequenced.
6. **Controlled UI Failure State**:
   - In the event of a fatal protocol or network error, the chat bubble renders an error state with a `Retry` action, preventing React component tree crashes.
7. **Performance Targets vs. Guarantees**:
   - First-token latency ($\le 1,000$ ms) and evidence render latency ($\le 500$ ms) are **measured performance targets** under normal operational conditions, not unconditional contractual guarantees.

---

## 9. Protected Ambient Invariants & Scope Compliance

- **Current Branch**: `develop`
- **HEAD Commit**: `65007729c21a56a8b532ec9ed4d4eae51b46e6b8`
- **Protected Files (100% Untouched)**:
  - `frontend/src/components/ambient/AmbientBackground.tsx`
  - `frontend/src/components/ambient/ambientBackground.ts`
  - `frontend/src/components/ambient/ambientBackground.test.ts`
- **Scope**: Documentation audit only. No application code, dependencies, or database migrations modified.
