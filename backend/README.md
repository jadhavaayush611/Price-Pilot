<div align="center">

# PricePilot Backend Service

### Enterprise Spring Boot 4.1 REST API & Marketplace Intelligence Engine

<p align="center">
  <img src="https://img.shields.io/badge/Java-21_LTS-orange.svg?style=flat-square&logo=openjdk" alt="Java 21 LTS">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.0-brightgreen.svg?style=flat-square&logo=springboot" alt="Spring Boot 4.1.0">
  <img src="https://img.shields.io/badge/Spring_Data_JPA-4.x-blue.svg?style=flat-square" alt="Spring Data JPA">
  <img src="https://img.shields.io/badge/Hibernate-7.4.1-brown.svg?style=flat-square&logo=hibernate" alt="Hibernate 7.4.1">
  <img src="https://img.shields.io/badge/Spring_Security-7.1-darkgreen.svg?style=flat-square&logo=springsecurity" alt="Spring Security 7.1">
  <img src="https://img.shields.io/badge/Tests-853_Passing-success.svg?style=flat-square" alt="853 Tests Passing">
</p>

<p align="center">
  <a href="../README.md"><strong>📖 Root Documentation</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-marketplace-intelligence-subsystem-v13"><strong>Marketplace Subsystem</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-rest-api-contracts"><strong>API Contracts</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-local-profiles--configuration"><strong>Configuration</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-testing--verification"><strong>Testing</strong></a>
</p>

</div>

---

> [!IMPORTANT]
> ### 🛡️ Marketplace Data Scope (v1.3)
> The backend marketplace subsystem operates **entirely offline** using local catalog data and simulated demonstration adapters. It does **not** scrape live merchant sites or connect to external production APIs. Live retail provider integration remains future work subject to authorized partner agreements.

---

## 🏛️ Architecture & Module Organization

The backend is organized as a clean modular monolith adhering to Domain-Driven Design (DDD) principles:

```text
backend/src/
├── main/java/com/pricepilot/
│   ├── analytics/               # Demand, interaction, and price trend metrics
│   ├── config/                  # Security, database seeder, CORS, and cache configs
│   ├── currency/                # Multi-currency conversion calculations
│   ├── exception/               # Global exception handlers and error contracts
│   ├── intelligence/
│   │   ├── marketplace/         # v1.3 Marketplace subsystem
│   │   │   ├── adapter/         # Offer adapter contracts and implementations
│   │   │   │   ├── MarketplaceOfferAdapter.java
│   │   │   │   ├── CatalogOfferAdapter.java
│   │   │   │   ├── MockMarketplaceOfferAdapter.java
│   │   │   │   └── MarketplaceOfferAggregatorService.java
│   │   │   ├── dto/             # Immutable normalized offer DTOs
│   │   │   ├── model/           # Enums (ProvenanceType, VerificationStatus, AvailabilityStatus)
│   │   │   ├── normalizer/      # OfferNormalizer (freshness, fee, ranking logic)
│   │   │   └── service/         # ProductOfferService
│   │   └── semantic/            # Deterministic vector search and embeddings
│   ├── pricehistory/            # Historical price shifts and timeline records
│   ├── product/                 # Core product entities, repositories, and controllers
│   ├── productprice/            # Multi-seller price relationships
│   ├── seller/                  # Merchant identities and metadata
│   ├── user/                    # User accounts and roles
│   └── watchlist/               # Price tracking targets and notifications
└── main/resources/
    ├── application.properties          # Base properties
    ├── application-local.properties    # In-memory H2 offline profile
    ├── application-dev.properties      # PostgreSQL dev profile
    └── db/migration/                   # Flyway deterministic migrations (V1.0 – V1.20)
```

---

## ⚡ Marketplace Intelligence Subsystem (v1.3)

### Core Workflow

```text
Client Request: GET /api/v1/products/{id}/offers
                       │
                       ▼
           ProductOffersController
                       │
                       ▼
              ProductOfferService (validates product existence; 404 if missing)
                       │
                       ▼
       MarketplaceOfferAggregatorService
           │                       │
           ▼                       ▼
  CatalogOfferAdapter    MockMarketplaceOfferAdapter
           │                       │
           └───────────┬───────────┘
                       │ RawMarketplaceOffer streams
                       ▼
                 OfferNormalizer
           ┌───────────────────────┐
           │ - Fee separation      │
           │ - Freshness evaluation│
           │ - Currency filtering  │
           │ - Deterministic sort  │
           └───────────────────────┘
                       │
                       ▼
            List<NormalizedOfferDTO>
```

### Data Normalization & Quality Invariants

1. **Separation of Base Price from Fees**:
   - `basePrice` is populated directly from verified listings.
   - If shipping, taxes, or merchant fees are absent, they remain `null` and `basePriceOnly: true` is asserted.
   - **Missing fees are never inferred as 0.00**.
   - `pricingDisclaimer` is always attached: *"Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout."*

2. **Freshness Semantics (The 168-Hour Boundary)**:
   - Stale threshold: **Strictly greater than 168 hours** (7 days).
   - $\le 168$ hours: Stale status is `false`, trust label is `"Catalog Offer"`.
   - $> 168$ hours: Stale status is `true`, trust label is `"Catalog Offer (Stale)"`.
   - Clock skew tolerance: Timestamps within 5 minutes ahead are accepted; timestamps $> 5$ minutes in the future are rejected as untrustworthy (`observedAt: null`, labeled `"Catalog Offer (Unverified Freshness)"`).
   - Missing timestamps: Preserved without crashing (`observedAt: null`, `isStale: false`, labeled `"Catalog Offer (Unverified Freshness)"`).

3. **Deterministic Ranking Rules**:
   Offers for any product are ordered by the following invariant:
   ```text
   1. basePrice ASC                (Lowest monetary value first)
   2. discountPercentage DESC       (Largest savings percentage first)
   3. sellerName ASC (case-sensitive ASCII)   (Deterministic lexicographical tie-breaker)
   ```
   - Legitimate promotional $0.00 base prices rank first.
   - Negative or null base prices return `null` and are discarded.
   - Non-USD or null currencies are rejected from canonical ranking without guessing exchange rates.

4. **Adapter Failure Isolation**:
   - `MarketplaceOfferAggregatorService` wraps every adapter invocation in defensive try-catch guards.
   - If an adapter throws an exception or times out, the error is logged and discarded. Surviving adapters continue to return valid offers.

---

## 📡 REST API Contracts

### Normalized Offers Endpoint

#### Request
```http
GET /api/v1/products/{id}/offers HTTP/1.1
Host: localhost:8080
Accept: application/json
```

#### Response: 200 OK (Product with offers)
```json
[
  {
    "id": "7c7b15f6-3029-4aad-be68-80d356b33646",
    "productId": "fed468d9-39c3-4eac-88be-a018a541afc1",
    "productTitle": "DJI Mic 2",
    "source": {
      "sellerId": null,
      "sellerName": "HP",
      "websiteUrl": "https://hp.com",
      "logoUrl": null,
      "offerUrl": "https://hp.com/product/fed468d9-39c3-4eac-88be-a018a541afc1",
      "providerName": "catalog"
    },
    "pricing": {
      "basePrice": 358.19,
      "originalPrice": 377.57,
      "discountPercentage": 5.13,
      "currency": "USD",
      "shippingPrice": null,
      "taxesAndFees": null,
      "totalPrice": null,
      "basePriceOnly": true,
      "pricingDisclaimer": "Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout."
    },
    "provenance": {
      "sourceType": "DEMO",
      "verificationStatus": "UNVERIFIED",
      "liveVerified": false,
      "observedAt": "2026-10-19T02:34:01.448676",
      "rawLastUpdated": "2026-10-19T02:34:01.448676",
      "stale": false,
      "trustLabel": "Catalog Offer",
      "trustDescription": "Demonstration catalog seed offer. Not a live verified retailer feed."
    },
    "availability": {
      "status": "UNKNOWN",
      "available": null,
      "stockQuantity": null
    },
    "isBestDeal": true,
    "savings": 19.38,
    "priceDifferenceFromLowest": 0.00
  }
]
```

#### Response: 200 OK (Product with no offers)
```json
[]
```

#### Response: 404 Not Found (Unknown product ID)
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 00000000-0000-0000-0000-000000000000"
}
```

---

## ⚙️ Local Profiles & Configuration

| Profile | Command Flag | Database | Flyway | Seed Data | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`local`** *(Default Demo)* | `--spring.profiles.active=local` | In-memory H2 | Disabled | Automatically seeds 252 products | Zero external dependency development |
| **`dev`** | `--spring.profiles.active=dev` | PostgreSQL `localhost:5432` | Enabled (V1.0–V1.20) | DatabaseSeeder | Full relational testing |
| **`test`** | Active during `mvnw test` | In-memory H2 | Disabled | Test-specific fixtures | Unit and integration testing |

### Starting with the Local Profile (No PostgreSQL required)
```bash
# Windows
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# macOS / Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 🧪 Testing & Verification

### Running the Full Test Suite
```bash
# Windows
.\mvnw.cmd test

# macOS / Linux
./mvnw test
```
*Current Suite Status*: **853 tests run, 0 failures, 0 errors, 0 skipped**.

### Focused Marketplace Test Execution
```bash
# Unit tests for OfferNormalizer
.\mvnw.cmd test -Dtest=OfferNormalizerTest

# Offline adapter framework & resilience tests
.\mvnw.cmd test -Dtest=MarketplaceAdapterFrameworkTest

# ProductOfferService tests
.\mvnw.cmd test -Dtest=ProductOfferServiceTest

# MockMvc REST endpoint controller tests
.\mvnw.cmd test -Dtest=ProductOffersControllerTest
```

---

## 🔮 Future Live Provider Integration Guidelines

When live external marketplace integrations are authorized in future releases:
1. **Provider Isolation**: Implement the `MarketplaceOfferAdapter` interface; do not modify `OfferNormalizer` or core catalog entities.
2. **Resilience Bounds**: All remote HTTP calls must enforce timeouts ($\le 2500$ms) and circuit breaker fallbacks.
3. **Credentials Management**: API keys and secrets must be injected strictly via environment variables (`System.getenv()`), never hardcoded in properties files.
4. **Data Verification**: Providers with authenticated feeds may declare `VerificationStatus.VERIFIED` only after automated contract testing.
