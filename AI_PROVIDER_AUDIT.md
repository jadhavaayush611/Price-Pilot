# PricePilot AI-02 — Read-Only Provider Audit (Refined Architecture)

**Target Milestone**: PricePilot v1.4 (AI-02)
**Target Service**: `pricepilot-ai` (FastAPI Inference Gateway)
**Companion Architecture**: Hybrid Orchestration (Spring Boot Orchestrator + FastAPI Inference Gateway + PostgreSQL Durable Store)
**Audit Date**: November 2026
**Status**: COMPLETED & REFINED (Read-Only)

---

## 1. Executive Summary

This audit assesses the existing LLM provider integration, model configuration, prompt construction, memory management, tool execution, and testing infrastructure inside the Python microservice (`pricepilot-ai`).

The current inference layer directly couples conversational routing to a single deprecated Google Gemini model (`gemini-1.5-flash`) via raw `requests.post` calls, relies on regex parsing of pseudo-XML tags for structured output, and stores session state in an ephemeral in-memory dictionary (`ConversationMemory`).

AI-02 will introduce a **clean, stateless provider abstraction** (`LlmProvider`), migrate model targeting to candidate **`gemini-3.8-flash`** with independently configurable fallback (**`gemini-2.5-flash`**), normalize provider errors into typed exceptions, introduce schema-validated structured output, enforce strict timeout budgets (3,500 ms max), and provide 100% offline mocked fixtures for CI/CD while preserving strict backward compatibility with Spring Boot's AI-01 synchronous contract.

---

## 2. As-Is Architecture & Codebase Inspection

### 2.1 Gemini Client Initialization & Configuration
- **Location**: [`pricepilot-ai/app/assistant/orchestrator.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/orchestrator.py#L23-L26)
- **Current Code**:
  ```python
  class AssistantOrchestrator:
      def __init__(self) -> None:
          self.api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
          self.model_name = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
  ```
- **Audit Findings**:
  1. `[VERIFIED FACT]` **No SDK Dependency**: The project does not install `google-generativeai` or `google-genai`. It makes raw HTTP POST calls using Python's `requests` library.
  2. `[VERIFIED FACT]` **Deprecated Default Model**: The default model is hardcoded as `gemini-1.5-flash` (discontinued).
  3. `[VERIFIED FACT]` **Direct URL Parameter Key Leakage**:
     ```python
     url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent?key={self.api_key}"
     ```
     The endpoint embeds the secret API key in the URL query string (`?key=...`), exposing credentials in HTTP access logs, proxy logs, and exception tracebacks.
  4. `[VERIFIED FACT]` **Rigid Generation Configuration**:
     `temperature: 0.2`, `topP: 0.8`, and `maxOutputTokens: 2048` are hardcoded in the method body (lines 99–103) with no runtime overrides or environment binding.

### 2.2 Prompt Construction & Response Parsing
- **Location**: [`pricepilot-ai/app/assistant/prompts.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/prompts.py), [`orchestrator.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/orchestrator.py#L71-L86), [`response_formatter.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/response_formatter.py)
- **Audit Findings**:
  1. `[VERIFIED FACT]` **Pseudo-XML Prompt Boundary Tagging**:
     `full_prompt` is manually assembled with custom text fencing:
     `<system_instructions>`, `<conversation_history>`, `<retrieved_context>`, `<user_query>`.
  2. `[VERIFIED FACT]` **Fragile Regex Extraction for Structured Data**:
     Instead of utilizing native Gemini Structured Outputs (`response_schema` / `response_mime_type: "application/json"`) or Tool Calling (`function_declarations`), the system instructs the model to emit custom tags:
     - `[BUY_CONFIDENCE] Score: X% Reason: ... [/BUY_CONFIDENCE]`
     - `[COMPARISON] { ... JSON ... } [/COMPARISON]`
     In [`response_formatter.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/response_formatter.py#L16-L45), regex matching (`re.search(r"\[BUY_CONFIDENCE\](.*?)...")`) extracts these blocks and strips them from user text. If the model introduces formatting quirks, JSON parsing fails silently and triggers heuristic fallbacks.

### 2.3 Conversation Context & Memory Management
- **Location**: [`pricepilot-ai/app/assistant/memory.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/memory.py)
- **Audit Findings**:
  1. `[VERIFIED FACT]` **In-Memory Ephemeral Storage**: `ConversationMemory._storage` is a Python process-level dictionary (`Dict[str, Dict[str, Any]]`).
  2. `[VERIFIED FACT]` **Violation of Gateway Statelessness**: The inference gateway retains conversational messages, active product IDs, user preferences, and searches in memory. Any container restart or horizontal scaling causes state loss.
  3. `[VERIFIED FACT]` **Architectural Inversion**: Spring Boot's PostgreSQL store is the authoritative source of truth for conversation history (`assistant_messages`). Maintaining a secondary, unsynchronized copy in FastAPI's memory causes drift.
  4. `[VERIFIED FACT]` **Legacy AI-01 Dependency**: Current `POST /assistant/chat` relies on `memory_manager.get_messages(conversation_id)` to format prompt history. AI-02 must maintain this behavior in the legacy path to avoid breaking backward compatibility before AI-06 (PostgreSQL 4-Tier Memory).

### 2.4 Error Handling & Timeout Behavior
- **Location**: [`pricepilot-ai/app/assistant/orchestrator.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/orchestrator.py#L106-L118), [`router.py`](file:///D:/PricePilot/pricepilot-ai/app/assistant/router.py#L64-L68)
- **Audit Findings**:
  1. `[VERIFIED FACT]` **FastAPI Client Timeout Mismatch**: `requests.post(url, json=payload, timeout=10)` has a 10.0-second timeout. However, Spring Boot's [`AiClientImpl`](file:///D:/PricePilot/backend/src/main/java/com/pricepilot/ai/AiClientImpl.java) enforces a hard 5.0-second cumulative deadline. A Gemini stall of 6–9 seconds causes Spring Boot to cut off the connection before FastAPI's client aborts.
  2. `[VERIFIED FACT]` **Silent Fallback Disparity**: In `orchestrator.py`, if Gemini fails or is unconfigured, it runs `_run_local_reasoning()`. If that succeeds, FastAPI returns HTTP 200 with local reasoning text, masking provider errors from Spring Boot.
  3. `[VERIFIED FACT]` **Fabricated Confidence**: In `_generate_fallback_buy_confidence()`, if price points are missing, it manufactures an arbitrary score of `70%`.

### 2.5 Security, Authentication & Secret Management
- **Location**: [`pricepilot-ai/app/api/endpoints.py`](file:///D:/PricePilot/pricepilot-ai/app/api/endpoints.py#L38-L52), [`settings.py`](file:///D:/PricePilot/pricepilot-ai/app/config/settings.py)
- **Audit Findings**:
  1. `[VERIFIED FACT]` **API Key Authentication**: FastAPI secures routes via `X-API-Key` header validated against `settings.api_key` (`PRICEPILOT_AI_API_KEY`).
  2. `[VERIFIED FACT]` **JWT Token Forwarding**: Spring Boot forwards user JWT authorization tokens to FastAPI via `Authorization: Bearer <token>`, used by FastAPI's `rag.py` to make authenticated loopback calls to Spring Boot.
  3. `[VERIFIED FACT]` **Insecure Dev Fallback**: `settings.py` allows default `"pricepilot-secret-api-key"` in non-production environments.

### 2.6 Dependencies and Existing Tests
- **Location**: [`pricepilot-ai/pyproject.toml`](file:///D:/PricePilot/pricepilot-ai/pyproject.toml), [`requirements.txt`](file:///D:/PricePilot/pricepilot-ai/requirements.txt), [`tests/test_assistant.py`](file:///D:/PricePilot/pricepilot-ai/tests/test_assistant.py)
- **Audit Findings**:
  1. `[VERIFIED FACT]` **FastAPI Stack**: `fastapi>=0.115.0`, `uvicorn>=0.30.0`, `pydantic>=2.8.0`, `requests>=2.32.0`, `httpx>=0.27.0`.
  2. `[VERIFIED FACT]` **Test Coverage**: [`test_assistant.py`](file:///D:/PricePilot/pricepilot-ai/tests/test_assistant.py) uses `unittest.TestCase` and `unittest.mock.patch` to mock `orchestrator.chat` at the endpoint level. There are currently **no provider-level unit tests** testing raw Gemini API payload assembly, response parsing, error handling, or rate-limit retries.

---

## 3. Component Reuse vs. Refactoring Assessment

| Component | File Path | Current Status | AI-02 Decision | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| `router.py` | `app/assistant/router.py` | Functional, validates `ChatRequest` | **REUSE & PRESERVE** | Maintains 100% backward compatibility with Spring Boot AI-01 integration contract. |
| `settings.py` | `app/config/settings.py` | Minimal env settings | **REFRESH** | Add typed provider configurations (`AI_PROVIDER`, `GEMINI_PRIMARY_MODEL`, `GEMINI_FALLBACK_MODEL`, `GEMINI_API_KEY`, `LLM_TIMEOUT_SECONDS`). |
| `orchestrator.py` | `app/assistant/orchestrator.py` | Monolithic, raw requests | **REFACTOR** | Decouple provider execution into `LlmProvider` abstraction. Delegate generation to provider adapter. |
| `LlmProvider` | `app/assistant/providers/base.py` | Non-existent | **CREATE** | Abstract base class defining provider-neutral contract (`generate_text`, `generate_structured`, `check_health`). |
| `GeminiProvider` | `app/assistant/providers/gemini.py` | Non-existent | **CREATE** | Concrete implementation for Gemini v1beta REST API with model agility, header auth, and error normalization. |
| `prompts.py` | `app/assistant/prompts.py` | Hardcoded prompt strings | **REUSE** | Core system and intent instructions remain sound for standard generation in AI-02. |
| `response_formatter.py` | `app/assistant/response_formatter.py` | Regex parsing of custom tags | **REUSE WITH SCHEMA ADAPTATION** | Retain fallback parsing while preparing structured response schemas for native JSON mode. |
| `memory.py` | `app/assistant/memory.py` | Volatile in-memory dict | **MAINTAIN AS LEGACY ADAPTER** | Keep existing interface temporarily to avoid breaking changes; scheduled for removal in AI-06 (PostgreSQL 4-Tier Memory). |
| `tools.py` & `rag.py` | `app/assistant/tools.py` | Pre-RAG HTTP loopbacks | **PRESERVE** | Preserved for legacy chat flow; AI-04 will transition tool execution strictly to Spring Boot. |

---

## 4. Key Constraints & Architecture Invariants for AI-02

1. **Stateless Gateway Principle**: FastAPI must act as a stateless inference gateway. Provider adapters must never retain conversation state or mutate local disks.
2. **Spring Boot Orchestrator Primacy**: Spring Boot remains the trusted authority. Model outputs must never authorize business operations (e.g. creating alerts, initiating purchases, mutating watchlists).
3. **Unified Timeout Budget**:
   - Spring Boot cumulative deadline: **5,000 ms**.
   - FastAPI Gemini provider budget: **3,500 ms maximum** (`LLM_TIMEOUT_SECONDS=3.5`).
   - Remaining budget: **~1,500 ms** reserved for local serialization, formatting, and network transit back to Spring Boot.
4. **Zero Retries by Default**: The synchronous provider path must execute with **0 automatic retries** to prevent violating the user-facing deadline.
5. **Model Agility & Independent Fallback Configuration**:
   - Primary model: `gemini-3.8-flash` (`GEMINI_PRIMARY_MODEL`).
   - Fallback model: `gemini-2.5-flash` (`GEMINI_FALLBACK_MODEL`).
   - Models are configured independently via environment variables. Fallback is strictly controlled by explicit error rules.
6. **Zero Live Key Requirement in CI/CD**: All unit and regression tests must execute offline using deterministic fixtures or HTTP mocking. Live API keys are strictly forbidden as a CI test requirement.
