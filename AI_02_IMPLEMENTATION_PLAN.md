# PricePilot AI-02 — Provider Abstraction & Model Integration Implementation Plan (Refined Master Revision)

**Target Milestone**: PricePilot v1.4 (AI-02)
**Target Service**: `pricepilot-ai` (FastAPI Inference Gateway)
**Prerequisite**: AI-01 Committed (`0dee349`)
**Status**: PROPOSED DESIGN (Pending Review)

---

## 1. Architectural Baseline & Objectives

The objective of AI-02 is to establish a **stateless, maintainable provider abstraction** (`LlmProvider`) in the existing FastAPI inference gateway, decoupling inference orchestration from vendor-specific REST endpoints.

AI-02 integrates **`gemini-3.8-flash`** as the primary candidate model with an independently configurable fallback model (**`gemini-2.5-flash`**), normalizes provider errors, enforces strict sub-5-second timeout budgets, introduces Pydantic-validated structured outputs, and provides 100% offline mocked fixtures for automated CI/CD.

```mermaid
graph TD
    subgraph Spring Boot Application Orchestrator (Port 8080)
        AiClient[AiClientImpl\n5.0s Cumulative Deadline\n0 Retries]
        DomainSvc[Domain Services & PostgreSQL]
    end

    subgraph FastAPI Inference Gateway (Port 8000)
        Router[POST /assistant/chat\nBackward-Compatible Contract]
        Orch[AssistantOrchestrator\nStateless Request Context Assembly]
        LegacyMem[(Legacy In-Memory MemoryManager\nTemporary Compatibility)]

        subgraph Provider Abstraction Layer
            BaseProvider[<<interface>>\nLlmProvider]
            GeminiAdapter[GeminiProvider\nStateless REST Adapter]
            MockAdapter[MockFixtureProvider\n100% Offline CI Fixtures]
        end
    end

    subgraph External LLM Service
        GeminiPrimary[Google Gemini API v1beta\nPrimary: gemini-3.8-flash]
        GeminiFallback[Google Gemini API v1beta\nFallback: gemini-2.5-flash]
    end

    AiClient -->|HTTP POST JSON (Sync)| Router
    Router --> Orch
    Orch -.->|Read Legacy Session State| LegacyMem
    Orch -->|LlmCompletionRequest| BaseProvider
    BaseProvider <|.. GeminiAdapter
    BaseProvider <|.. MockAdapter

    GeminiAdapter -->|HTTP POST (3.5s Timeout, x-goog-api-key)| GeminiPrimary
    GeminiAdapter -.->|On Permitted 404/503 Only| GeminiFallback
```

---

## 2. Refined Architectural Decisions

### 2.1 Model Selection & Permitted Fallback Rules

#### Model Configuration
- **Primary Model**: `gemini-3.8-flash` (Candidate, configured via `GEMINI_PRIMARY_MODEL`).
- **Fallback Model**: `gemini-2.5-flash` (Secondary candidate, configured via `GEMINI_FALLBACK_MODEL`).
- Both models are configured independently as environment variables and resolved in `settings.py`. Neither model's availability is assumed without verification.

#### Explicit Model Fallback Matrix
Model fallback (attempting generation with `GEMINI_FALLBACK_MODEL` after `GEMINI_PRIMARY_MODEL` fails) is governed by strict criteria:

| Upstream Error / Status Code | Error Classification | Permit Model Fallback? | Action Taken | Rationale |
| :--- | :--- | :---: | :--- | :--- |
| **HTTP 404 Not Found** | Model Unavailable / Deprecated | **YES** | Log warning, retry once on `GEMINI_FALLBACK_MODEL` within remaining timeout budget. | The primary model identifier may be invalid or retired in the target region. |
| **HTTP 503 Service Unavailable** | Model Overloaded / Capacity Exceeded | **YES** | Log warning, retry once on `GEMINI_FALLBACK_MODEL` within remaining timeout budget. | Upstream Google infrastructure may experience temporary capacity limits on preview models. |
| **HTTP 429 Resource Exhausted** *(Model-specific quota)* | Model Rate Limit | **YES** | Log warning, retry once on `GEMINI_FALLBACK_MODEL` if error reason is model-specific capacity. | Primary candidate quota exhausted while secondary tier may have capacity. |
| **HTTP 401 Unauthorized** | Invalid API Key | **NO** | Immediately raise `ProviderAuthError`. Do not downgrade. | Credential issue is project-wide; model switching burns latency and will fail identically. |
| **HTTP 403 Forbidden** | Project Disabled / Permission Denied | **NO** | Immediately raise `ProviderAuthError`. Do not downgrade. | Project permission failure applies to all models under the credentials. |
| **HTTP 400 Bad Request** | Malformed Payload / Invalid Parameter | **NO** | Immediately raise `ProviderInvalidRequestError`. Do not downgrade. | Input formatting error; switching models will fail identically. |
| **Schema Validation Error** | Malformed Model Output JSON | **NO** | Reject output, raise `StructuredOutputValidationError`. Do not downgrade. | Silent switching masks validation issues and introduces non-deterministic latency. |
| **Content Filter / Safety Block** | Safety Policy Violation | **NO** | Raise `ProviderContentFilterError`. Do not downgrade. | Fails safely per security policy; prompt reformulation required. |

#### Generation Provenance Recording
Every completion response tracks the actual provider and model executed:
- `LlmCompletionResponse.model_name`: exact model executed (e.g. `"gemini-3.8-flash"` or `"gemini-2.5-flash"`).
- `LlmCompletionResponse.provider_name`: `"gemini"` or `"mock"`.
- `LlmCompletionResponse.fallback_used`: boolean flag indicating whether fallback occurred.
- FastAPI includes `X-AI-Model: {model_name}` and `X-AI-Provider: {provider_name}` in HTTP response headers for end-to-end auditability.

---

### 2.2 Memory Ownership & Safe Stateless Migration Boundary

1. **FastAPI Is Stateless**:
   The inference gateway is strictly an execution channel. Provider adapters (`LlmProvider`, `GeminiProvider`) must remain 100% stateless, receiving all conversational history and system instructions per-request and maintaining zero local session state.
2. **Temporary Legacy Compatibility**:
   [`ConversationMemory`](file:///D:/PricePilot/pricepilot-ai/app/assistant/memory.py) (`memory_manager`) is retained strictly as an internal compatibility shim for the legacy `POST /assistant/chat` endpoint during AI-02.
3. **Stateless Request Context Assembly**:
   In `AssistantOrchestrator.chat()`:
   ```python
   # Legacy retrieval from in-memory store during AI-02
   raw_history = memory_manager.get_messages(conversation_id)

   # Formulate into stateless provider-neutral message contract
   llm_messages = [
       LlmMessage(role=LlmRole.USER if m["role"] == "user" else LlmRole.ASSISTANT, content=m["content"])
       for m in raw_history
   ]
   ```
4. **AI-06 Migration Boundary**:
   In AI-06, Spring Boot will query PostgreSQL `assistant_messages` directly and supply `List<LlmMessage>` in the request payload. `ConversationMemory` will be deleted from FastAPI without requiring any changes to the `LlmProvider` interface.

---

### 2.3 Unified Timeout & Retry Policy

#### Budget Hierarchy
Spring Boot enforces a hard cumulative ceiling of **5,000 ms** across the entire round-trip. To ensure FastAPI never exceeds this deadline:

```
[0.0s ────────────────────────────── 3.5s ────────────── 4.0s ────────────────────────── 5.0s]
 │<── FastAPI Provider Budget ──────>│                    │                                │
 │    (LLM_TIMEOUT_SECONDS = 3.5s)   │                    │                                │
 │                                   │<── Gateway Local ─>│                                │
 │                                   │    Processing      │<── Network Return Latency ────>│
 │                                   │    & Validation    │    Back to Spring Boot         │
```

- **Spring Boot Total Deadline**: `5,000 ms` (enforced via `JdkClientHttpRequestFactory`).
- **FastAPI Provider Budget**: **`3,500 ms` maximum** (`LLM_TIMEOUT_SECONDS = 3.5`).
- **Gateway Reserve Budget**: **`~1,500 ms`** allocated for local context retrieval, Pydantic validation, response formatting, and network transit back to Spring Boot.

#### Retry Policy
- **Zero Retries by Default**: The synchronous chat path executes with **0 retries** to preserve the 5.0-second user-facing budget.
- **Budget-Aware Model Fallback**: If `GEMINI_PRIMARY_MODEL` encounters a permitted fallback error (404/503), the fallback attempt on `GEMINI_FALLBACK_MODEL` is only executed if the remaining time in the 3.5s provider budget exceeds **1,500 ms**. If remaining budget $< 1,500$ ms, the provider aborts immediately and raises `ProviderTimeoutError`, allowing Spring Boot to cleanly invoke `DeterministicShoppingAssistantFallback`.
- **Error Normalization**: All network stalls, read timeouts, and connect failures wrap into typed `ProviderTimeoutError` with measured latency.

---

### 2.4 Structured Output & Schema-Validation Strategy

1. **Provider-Neutral Structured Contract**:
   `LlmProvider.generate_structured(request, schema_class: Type[T]) -> Tuple[T, LlmCompletionResponse]`
2. **Native Gemini JSON Schema Support**:
   When structured output is requested, `GeminiProvider` configures:
   ```json
   {
     "generationConfig": {
       "responseMimeType": "application/json",
       "responseSchema": schema_class.model_json_schema()
     }
   }
   ```
3. **Pydantic Validation**:
   - The raw JSON response string is parsed and validated directly against the Pydantic schema (e.g. `BuyConfidenceSchema`, `ProductComparisonSchema`).
   - If validation fails, `StructuredOutputValidationError` is raised. Incomplete or invalid schema objects are strictly rejected.
4. **Temporary Compatibility Fallback**:
   To preserve existing AI-01 behavior, [`response_formatter.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/response_formatter.py) regex tag stripping remains active for unstructured text generation where tags are present in legacy prompts.
5. **Business Operation Boundary**:
   **Model output is never permitted to authorize a business operation**. Price alert creation, product comparison persistence, watchlist modification, and checkout triggers remain solely authored, validated, and executed within Spring Boot domain services.

---

### 2.5 Provider Abstraction Interface Design

The provider abstraction avoids premature generic complexity, maintaining a focused contract designed specifically for the gateway's inference needs.

`app/assistant/providers/base.py`:
```python
from abc import ABC, abstractmethod
from typing import Dict, Any, Type, TypeVar, Tuple
from app.assistant.providers.contracts import (
    LlmCompletionRequest,
    LlmCompletionResponse
)
from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)

class LlmProvider(ABC):
    """Stateless provider interface for Large Language Model inference."""

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Provider identifier ('gemini', 'mock')."""
        pass

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Currently active primary model name."""
        pass

    @abstractmethod
    def generate_text(self, request: LlmCompletionRequest) -> LlmCompletionResponse:
        """Generates plain text response using configured candidate model."""
        pass

    @abstractmethod
    def generate_structured(self, request: LlmCompletionRequest, schema_class: Type[T]) -> Tuple[T, LlmCompletionResponse]:
        """Generates schema-validated structured response using native provider schema mode."""
        pass

    @abstractmethod
    def check_health(self) -> Dict[str, Any]:
        """Verifies API credentials and model accessibility without text generation."""
        pass
```

---

## 3. File-Level Implementation Plan

```
pricepilot-ai/
├── app/
│   ├── config/
│   │   └── settings.py                        # REFACTOR: Add GEMINI_PRIMARY_MODEL, GEMINI_FALLBACK_MODEL, timeouts
│   └── assistant/
│       ├── providers/
│       │   ├── __init__.py                    # NEW: Export provider factory and contracts
│       │   ├── base.py                        # NEW: LlmProvider abstract base class
│       │   ├── contracts.py                   # NEW: LlmCompletionRequest, LlmCompletionResponse, LlmRole, LlmUsage
│       │   ├── exceptions.py                  # NEW: Normalized ProviderError exception hierarchy
│       │   ├── gemini.py                      # NEW: GeminiProvider implementation (REST v1beta, header auth)
│       │   ├── mock.py                        # NEW: MockFixtureProvider for 100% offline CI testing
│       │   └── schemas.py                     # NEW: Pydantic schemas for structured generation (BuyConfidence, Comparison)
│       ├── orchestrator.py                    # REFACTOR: Delegate inference to LlmProvider, record provenance
│       ├── response_formatter.py              # REFACTOR: Support typed structured output alongside legacy fallback
│       └── router.py                          # PRESERVE: Keep POST /assistant/chat contract 100% backward-compatible
├── tests/
│   ├── fixtures/
│   │   ├── gemini_success_response.json       # NEW: Raw Gemini API response fixture
│   │   ├── gemini_structured_response.json    # NEW: Structured JSON response fixture
│   │   └── gemini_error_responses.json        # NEW: 400, 401, 404, 429, 503 error fixtures
│   ├── test_assistant.py                      # PRESERVE & UPDATE: Endpoint routing and backward compatibility
│   └── test_gemini_provider.py                # NEW: Comprehensive offline unit test suite for GeminiProvider
└── scripts/
    └── verify_gemini_live.py                  # NEW: Standalone diagnostic verification script (never run in CI)
```

---

## 4. Exact Implementation Sequence

### Phase 1: Configuration & Error Hierarchy
1. Update `app/config/settings.py` to declare typed configuration properties:
   - `gemini_primary_model: str = "gemini-3.8-flash"`
   - `gemini_fallback_model: str = "gemini-2.5-flash"`
   - `gemini_api_key: Optional[str] = None`
   - `llm_timeout_seconds: float = 3.5`
2. Create `app/assistant/providers/exceptions.py` defining `ProviderError`, `ProviderAuthError`, `ProviderRateLimitError`, `ProviderTimeoutError`, `ProviderInvalidRequestError`, and `StructuredOutputValidationError`.

### Phase 2: Provider Contracts & Abstraction
3. Create `app/assistant/providers/contracts.py` with Pydantic schemas: `LlmRole`, `LlmMessage`, `LlmCompletionRequest`, `LlmUsage`, and `LlmCompletionResponse`.
4. Create `app/assistant/providers/schemas.py` defining typed structured output schemas: `BuyConfidenceSchema` and `ComparisonResultSchema`.
5. Create `app/assistant/providers/base.py` defining `LlmProvider`.

### Phase 3: Gemini Provider Implementation
6. Create `app/assistant/providers/gemini.py`:
   - Initialize `requests.Session` with `timeout=settings.llm_timeout_seconds`.
   - Implement `x-goog-api-key` header authentication.
   - Implement `generate_text()` with model fallback logic conforming strictly to Section 2.1.
   - Implement `generate_structured()` using Gemini's native `responseSchema` and Pydantic validation.
   - Implement `check_health()` via lightweight `models.get` request.
7. Create `app/assistant/providers/mock.py`:
   - Implement `MockFixtureProvider` supporting deterministic canned responses and simulated exception scenarios.

### Phase 4: Orchestrator Integration & Backward Compatibility
8. Refactor `app/assistant/orchestrator.py`:
   - Inject `LlmProvider` via provider factory (`ProviderFactory.get_provider()`).
   - Replace raw `requests.post` call with `provider.generate_text()`.
   - Record `model_name`, `provider_name`, and latency in output dictionary.
   - Retain legacy memory calls temporarily to guarantee zero breakage for existing AI-01 tests.

### Phase 5: Test Suite Implementation & Verification
9. Add mock fixtures in `tests/fixtures/`.
10. Implement `tests/test_gemini_provider.py` covering the complete test matrix.
11. Run pytest suite and verify 100% offline execution.
12. Create `scripts/verify_gemini_live.py`.

---

## 5. Offline Test Matrix (`tests/test_gemini_provider.py`)

All tests must execute 100% offline without live internet access or external API keys:

| Test Case | Scenario / Condition | Expected Behavior |
| :--- | :--- | :--- |
| `test_payload_construction` | `LlmCompletionRequest` converted to Gemini format | Headers include `x-goog-api-key`; system instructions separated; contents properly structured. |
| `test_api_key_header_no_url_leak` | Request URL inspection | API key is **never** in URL query parameter `?key=...`. |
| `test_generate_text_success` | Mocked HTTP 200 with standard candidate payload | Returns `LlmCompletionResponse` with text, token usage, model `"gemini-3.8-flash"`, `fallback_used=False`. |
| `test_generate_structured_success` | Mocked HTTP 200 with JSON Schema payload | Pydantic model (`BuyConfidenceSchema`) successfully instantiated and validated. |
| `test_structured_validation_failure` | Mocked HTTP 200 with invalid JSON schema | Raises `StructuredOutputValidationError`; does not silently switch models. |
| `test_model_fallback_on_404` | Primary model returns HTTP 404 | Retries on `gemini-2.5-flash`; completes successfully with `model_name="gemini-2.5-flash"`, `fallback_used=True`. |
| `test_model_fallback_on_503` | Primary model returns HTTP 503 | Retries on `gemini-2.5-flash`; completes successfully with `fallback_used=True`. |
| `test_no_fallback_on_401` | Primary model returns HTTP 401 | Immediately raises `ProviderAuthError`; does not attempt fallback model. |
| `test_no_fallback_on_400` | Primary model returns HTTP 400 | Immediately raises `ProviderInvalidRequestError`; does not attempt fallback model. |
| `test_provider_timeout_budget_enforcement` | Mocked HTTP request takes $> 3.5$s | Aborts socket request, raises `ProviderTimeoutError`; duration bounded by 3,500 ms. |
| `test_rate_limit_normalization` | Mocked HTTP 429 | Raises `ProviderRateLimitError` with retry-after header parsing. |
| `test_backward_compatibility_chat` | Request to `POST /assistant/chat` | Output JSON contains expected `response`, `conversationId`, `products`, and new `X-AI-Model` header. |

---

## 6. Configuration & Secret Requirements

### Environment Variables
| Variable Name | Required | Default Value | Description |
| :--- | :---: | :--- | :--- |
| `AI_PROVIDER` | No | `"gemini"` | Active provider selection (`"gemini"`, `"mock"`). |
| `GEMINI_PRIMARY_MODEL` | No | `"gemini-3.8-flash"` | Primary candidate model identifier. |
| `GEMINI_FALLBACK_MODEL` | No | `"gemini-2.5-flash"` | Fallback candidate model identifier on 404/503. |
| `GEMINI_API_KEY` | Optional in Dev / CI | `None` | Google AI Studio Developer API Key. |
| `LLM_TIMEOUT_SECONDS` | No | `3.5` | Strict timeout budget for Gemini provider HTTP calls. |
| `PRICEPILOT_AI_API_KEY` | Yes (in Prod) | `"pricepilot-secret-api-key"` | Gateway authentication secret matching Spring Boot. |

---

## 7. Risks, Invariants & Unresolved Questions

### Architecture Invariants
- **Stateless Gateway**: FastAPI retains no durable conversational state.
- **Spring Boot Authority**: All database writes, tool executions, and security policies remain strictly inside Spring Boot.
- **Zero Live Keys in CI**: CI pipelines must pass without `GEMINI_API_KEY` set.

### Identified Risks & Mitigations
1. **Model Identifier Availability**:
   - *Risk*: `gemini-3.8-flash` is an experimental preview model and may change endpoint naming or API versions.
   - *Mitigation*: Both primary and fallback identifiers are configurable independently via environment variables. If `gemini-3.8-flash` returns 404, `GeminiProvider` automatically shifts to `gemini-2.5-flash` while logging an alert.
2. **Timeout Exhaustion on Fallback**:
   - *Risk*: If the primary model takes 3.0s before failing with 503, attempting a fallback model could breach Spring Boot's 5.0s client deadline.
   - *Mitigation*: The fallback attempt is skipped if remaining provider budget is $< 1,500$ ms, failing fast to Spring Boot's deterministic fallback.
