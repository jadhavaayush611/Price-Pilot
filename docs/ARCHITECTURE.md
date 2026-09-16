# PricePilot Architecture Overview

PricePilot follows a clean, layered architecture designed for separation of concerns, scalability, and ease of maintainability.

---

## 1. High-Level System Architecture

The project consists of three primary tiers:

```
┌────────────────────────────────────────────────────────┐
│                   Client Browser                       │
│  - Executes React UI & Framer Motion animations        │
│  - Communicates with API via Axios (using localhost)   │
└──────────────────────────┬─────────────────────────────┘
                           │
                           │ HTTP/REST (Port 80/8080)
                           ▼
┌────────────────────────────────────────────────────────┐
│             Nginx Frontend Container (Port 80)          │
│  - Serves static compiled React & HTML/CSS/JS assets   │
│  - Custom routing resolves paths to index.html         │
└──────────────────────────┬─────────────────────────────┘
                           │
                           │ Internal Network API Calls
                           ▼
┌────────────────────────────────────────────────────────┐
│          Spring Boot Backend Container (Port 8080)     │
│  - Active Profile: Prod (Java 25 runtime environment)  │
│  - Manages REST endpoints, mappings, validations       │
└──────────────────────────┬─────────────────────────────┘
                           │
                           │ PostgreSQL TCP (Port 5432)
                           ▼
┌────────────────────────────────────────────────────────┐
│            PostgreSQL DB Container (Port 5432)         │
│  - Persistent Volume: postgres_data                    │
│  - Source of truth for products, sellers, and prices  │
└────────────────────────────────────────────────────────┘
```

---

## 2. Backend Design Patterns

The backend code is organized into feature-driven packages and strictly implements the standard **Controller → Service → Repository** model:

```
User Request ──> Controller ──> Service ──> Repository ──> PostgreSQL
      DTO <── Response DTO <── Service <── JPA Entity <── Database
```

### Layer Responsibilities
1. **Controller Layer (REST Endpoints):**
   * Exposes endpoints (e.g. `ProductController`, `SellerController`).
   * Handles HTTP routing, input validation (`jakarta.validation.Valid`), and CORS headers (`@CrossOrigin(origins = "*")`).
   * **Rule:** Controllers never communicate with the database directly. They accept Request DTOs and return Response DTOs.
2. **Service Layer (Business Logic):**
   * Encapsulates core algorithms (e.g. `ProductService`, `ProductPriceService`).
   * Computes price comparisons, discount amounts, and discount percentages.
   * Handles transactional boundaries (`@Transactional`).
   * Maps database entities to DTOs.
   * **Rule:** Services orchestrate domain updates and use repositories to read/write database state.
3. **Repository Layer (Data Access):**
   * Declares interfaces (e.g. `ProductRepository`, `ProductPriceRepository`) extending `JpaRepository`.
   * Implements custom queries (JPQL or Specifications) for multi-faceted filters and sorting.
   * **Rule:** Isolated to SQL execution and Hibernate translation.

---

## 3. Database Schema & Domain Relationships

The data model connects products to multiple competing sellers via the price record:

```mermaid
erDiagram
    Product ||--o{ ProductPrice : has_prices
    Seller ||--o{ ProductPrice : lists_prices
    
    Product {
        UUID id PK
        varchar name
        varchar brand
        text description
        varchar category
        varchar imageUrl
        timestamp createdAt
        timestamp updatedAt
    }

    Seller {
        UUID id PK
        varchar name
        varchar websiteUrl
        varchar logoUrl
        timestamp createdAt
        timestamp updatedAt
    }

    ProductPrice {
        UUID id PK
        UUID productId FK
        UUID sellerId FK
        numeric currentPrice
        numeric originalPrice
        numeric discountPercentage
        varchar productUrl
        timestamp lastUpdated
        timestamp createdAt
        timestamp updatedAt
    }
```

* **One-to-Many Relationships:**
  * One `Product` has many `ProductPrice` entries.
  * One `Seller` has many `ProductPrice` entries.
* **Many-to-One Relationships:**
  * `ProductPrice` references exactly one `Product` and one `Seller`.

---

## 4. Frontend Architecture

The frontend is a lightweight Single Page Application (SPA) built on React 19, TypeScript, and Vite 8:
* **Tailwind CSS v4:** Handles all modern styling, utilizing utility classes.
* **Framer Motion:** Power-packs high-end responsive animations and micro-interactions for a premium feel.
* **Lucide Icons:** Provides standard high-quality system icons.
* **Axios API Client:** Coordinates JSON data fetch requests from the backend API.
* **Environment variables:** Resolves `import.meta.env.VITE_API_BASE_URL` to route requests to the container-exposed backend.

---

## 5. Deployment Orchestration

* **Health Orchestration:** Prevents the backend from starting before the database is ready, and prevents the frontend from starting before the backend is ready.
* **Docker Network:** All three services communicate on a private bridge network (`pricepilot-network`). Only backend and frontend ports are exposed to the host machine for safety.
* **Data Volume:** Persists PostgreSQL records across container restarts (`postgres_data`).

---

## 6. Personalized Shopping Intelligence (v1.2)

The Personalized Shopping Intelligence subsystem establishes an immutable domain layer that captures and normalizes personalization context for intelligent product ranking and recommendations.

### Core Architectural Principle
> **"Personalization changes ranking and selection among objectively valid candidates. It does not change product facts or override hard constraints."**

### Key Subsystem Components
1. **PersonalizationContext (`com.pricepilot.intelligence.personalization.context.PersonalizationContext`):**
   * Immutable, user-scoped domain object representing the complete personalization state for a single intelligence request.
   * Completely uncoupled from JPA persistence, HTTP sessions, database entities, and recommendation algorithms.
2. **Provenance & Source Distinction (`PersonalizationSource`):**
   * Preserves explicit provenance: `EXPLICIT_PREFERENCE` (user-configured shopping profile) vs `BEHAVIORAL_SIGNAL` (deterministic aggregated interactions).
   * Enforces the architectural rule: `EXPLICIT_PREFERENCE > BEHAVIORAL_SIGNAL` in confidence and ranking authority without prematurely collapsing signals into an undifferentiated number.
3. **Bounded Signal Representation (`SignalStrength` & `PersonalizationSignal`):**
   * Signal strength is strictly bounded in the range `[0.0, 1.0]`, rejecting NaN, infinite, and out-of-bounds values to prevent unbounded personalization bias.
4. **Deterministic Ordering & Construction (`PersonalizationContextBuilder`):**
   * Sorts signals by `(signalType, source, targetKey, normalizedValue, strength)` deterministically, ensuring `Context A.equals(Context B)` regardless of input collection sequencing or JVM map iteration order.
   * Deduplicates duplicate signals while retaining the highest signal strength.
5. **Hard-Constraint Boundary:**
   * Personalization context provides preference guidance only and cannot override deterministic product facts or hard shopping filters (e.g. strict budget bounds, stock availability, category boundaries, security policies).
6. **Privacy Boundary:**
   * Stores normalized domain signals only (e.g. preferred categories, normalized brand affinity). Does not store raw clickstreams, search transcripts, timestamps of individual events, or private activity logs in context.
7. **Explicit Preference Integration (`com.pricepilot.intelligence.personalization.preference.adapter.ExplicitPreferenceAdapter`):**
   * Adapts the existing v1.1 explicit preference system (`UserShoppingPreferenceService` / `UserShoppingPreferenceEntity`) into the normalized `PersonalizationContext`.
   * **Single Source of Truth:** Existing v1.1 preference storage remains authoritative; no duplicate preference tables or entities are introduced.
   * **Cache Reuse:** Directly leverages the existing Spring/Redis `@Cacheable("user-preferences")` cache layer without duplicate caching mechanisms.
   * **Explicit Provenance:** All mapped signals are tagged strictly with `PersonalizationSource.EXPLICIT_PREFERENCE` (no behavioral contamination).
   * **Missing Preference & Partial Handling:** Users without persisted preferences receive a valid empty `PersonalizationContext` with zero fabricated signals.
   * **Failure Isolation:** Persistence errors gracefully fall back to an empty context for uninterrupted base intelligence, while security and authorization exceptions are preserved.
   * **Zero AI/API-Key Dependency:** Entire pipeline operates 100% locally and deterministically.
8. **Behavioral Signal Integration (`com.pricepilot.intelligence.personalization.signals.adapter.BehavioralSignalAdapter`):**
   * Adapts the existing v1.1 behavioral extraction system (`BehavioralSignalService` / `UserShoppingSignals`) into the normalized `PersonalizationContext`.
   * **Single Source of Truth:** Existing v1.1 behavioral aggregation remains authoritative; the adapter does NOT reprocess raw events or create duplicate event repositories.
   * **Safeguard Reuse:** Directly preserves existing 3-minute deduplication windows, replay protection, and satiation caps (max 5 views/interactions per product) enforced by `BehavioralSignalServiceImpl`.
   * **Behavioral Provenance:** All mapped signals are tagged strictly with `PersonalizationSource.BEHAVIORAL_SIGNAL` with strength bounded in $[0.0, 1.0]$.
   * **Explicit/Behavioral Coexistence:** Signals targeting the same dimension (e.g., category "laptops") coexist as distinct signals (`CATEGORY_PREFERENCE` vs `CATEGORY_AFFINITY`) without overwriting each other.
   * **Privacy & User Isolation:** Context contains only aggregate affinity signals (category, brand, product interaction); no raw clickstreams, timestamps, or IP addresses are exposed.
   * **Missing History & Failure Isolation:** Users with zero interactions receive an empty context with no fabricated signals; unexpected retrieval errors degrade gracefully to an empty behavioral context.
9. **Personalized Scoring Engine (`com.pricepilot.intelligence.personalization.scoring`):**
   * Computes bounded, deterministic personalized score adjustments on product candidates based exclusively on `PersonalizationContext`.
   * **Core Mathematical Formula:**
     $$\text{PersonalizedScore} = \text{BaseScore} + \text{BoundedPersonalizationAdjustment}$$
     $$\text{Adjustment} \in [-30.0, +35.0]$$
     $$\text{FinalScore} = \text{clamp}(\text{round}(\text{BaseScore} + \text{Adjustment}, 1), 0.0, 100.0)$$
   * **Empty Context Neutrality:** When `PersonalizationContext.isEmpty()`, `Adjustment = 0.0` and `FinalScore == BaseScore`.
   * **Hard Constraints & Product Invariance:** Scoring operates as a pure ranking modifier. It cannot mutate underlying product facts, alter candidate availability, or bypass security/eligibility boundaries.
   * **Weighted Signals & Evidence:**
     * Preferred Category Match: `+12.0` (`PREFERRED_CATEGORY`)
     * Preferred Brand Match: `+10.0` (`PREFERRED_BRAND`)
     * Within Budget: `+8.0` (`WITHIN_BUDGET`) / Exceeds Budget: `-10.0` (`EXCEEDS_BUDGET`)
     * Rating Threshold Met: `+6.0` (`RATING_CRITERIA_MET`)
     * High Deal Sensitivity Match: `+8.0` (`DEAL_SENSITIVITY_MATCH`)
     * In-Stock Preference Match: `+4.0` / Out of Stock Penalty: `-20.0` (`LIMITED_AVAILABILITY`)
     * Behavioral Category Affinity: `affinity * 6.0` (`BEHAVIORAL_AFFINITY`)
     * Behavioral Brand Affinity: `affinity * 4.0` (`BEHAVIORAL_AFFINITY`)
     * Behavioral Interaction Affinity: `+3.0` (`BEHAVIORAL_AFFINITY`)
   * **Deterministic 5-Tier Tie-Breaking:** Candidate ranking strictly breaks ties across: (1) final score desc, (2) base score desc, (3) personalization adjustment desc, (4) lowest price asc, and (5) lexicographical product UUID asc.
10. **Personalized Evidence (`com.pricepilot.intelligence.personalization.evidence`):**
    * Generates deterministic, grounded explainability evidence explaining the `PersonalizedScore` generated by Phase 6.4.
    * **Core Invariant:** Evidence explains the score; evidence never creates, modifies, or recalculates the score.
    * **Grounding & Provenance:**
      * Inspects the structured scoring breakdown in `PersonalizedScore` and verifies supporting facts against `ProductResponseDTO` and `PersonalizationContext`.
      * Explicit preferences produce factual match statements (`PREFERRED_CATEGORY`, `PREFERRED_BRAND`, `WITHIN_BUDGET`, `RATING_CRITERIA_MET`, `DEAL_SENSITIVITY_MATCH`).
      * Behavioral signals produce non-prescriptive affinity statements (`BEHAVIORAL_AFFINITY` for recent activity and brand interest) without claiming explicit user configuration.
    * **Privacy Boundary:** Evidence descriptions strictly omit raw clickstreams, timestamps, IP addresses, or internal event identifiers.
    * **Deterministic Ordering:** Evidence items are ordered deterministically by importance descending, then EvidenceType name, then description.
    * **Immutability & Safety:** All evidence collections are exposed via unmodifiable lists in `PersonalizedEvidence`. Zero persistence, Redis, or external AI dependencies.