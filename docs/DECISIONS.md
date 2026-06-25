# PricePilot — Architecture Decision Log (ADR)

This document records important architectural decisions made throughout the development of PricePilot.

Its purpose is to document not only **what** decisions were made, but **why** they were made and the trade-offs considered.

---

# ADR-001 — Layered Architecture

## Decision

Use a layered architecture:

Controller → Service → Repository → Database

## Rationale

* Clear separation of responsibilities.
* Easier testing.
* Maintainable codebase.
* Independent evolution of business logic and persistence.

---

# ADR-002 — Spring Data JPA

## Decision

Use Spring Data JPA with Hibernate instead of raw JDBC.

## Rationale

* Reduces boilerplate.
* Supports transactions.
* Provides entity lifecycle management.
* Simplifies repository development.

Raw SQL remains available for performance-critical operations.

---

# ADR-003 — UUID Primary Keys

## Decision

Use UUIDs as primary keys instead of auto-increment integers.

## Rationale

* Harder to enumerate resources.
* Better support for distributed systems.
* Easier future microservice migration.

---

# ADR-004 — Domain-Oriented Package Structure

## Decision

Organize code by business domain rather than technical layer.

Example:

* product
* seller
* watchlist
* analytics

instead of grouping all controllers, services, or repositories together.

## Rationale

Improves scalability and keeps related functionality together.

---

# ADR-005 — JWT Stores User UUID

## Decision

Store user UUID and role as JWT claims.

## Rationale

Avoid repeated database lookups on authenticated requests while keeping authorization checks straightforward.

---

# ADR-006 — Immutable Interaction Events

## Decision

User interaction events are append-only.

Events cannot be updated or deleted.

## Rationale

Immutable events preserve behavioral history, support analytics, and enable future recommendation and ML pipelines.

---

# ADR-007 — Separate Analytics Domain

## Decision

Analytics data is stored independently from Product entities.

## Rationale

Products represent business data.

Analytics represents behavioral data.

Separating these concerns keeps the domain model clean and avoids bloated entities.

---

# ADR-008 — Rule-Based Recommendation Engine

## Decision

Implement deterministic recommendations before Machine Learning.

## Rationale

Allows the platform to deliver personalized results immediately while establishing clean interfaces for future ML integration.

---

# ADR-009 — RecommendationService Abstraction

## Decision

Controllers depend on the RecommendationService interface rather than a concrete implementation.

## Rationale

Future recommendation engines (Python/FastAPI, vector search, or ML services) can replace the current implementation without modifying controllers or API contracts.

---

# ADR-010 — Dashboard Aggregation

## Decision

Expose a single dashboard endpoint that aggregates data from multiple domains.

## Rationale

Reduces frontend network requests, provides a consistent snapshot of user data, and simplifies caching strategies.

---

# ADR-011 — Redis for Read Optimization

## Decision

Cache recommendation and dashboard responses in Redis.

## Rationale

These endpoints are read-heavy and computationally expensive compared to standard CRUD operations.

Caching improves responsiveness while reducing database load.

---

# ADR-012 — Flyway for Schema Management

## Decision

Use Flyway for all database schema changes.

## Rationale

Version-controlled migrations provide reproducible deployments and eliminate manual database drift.

---

# ADR-013 — Service Layer Owns Business Logic

## Decision

Business rules must reside in the Service layer.

## Rationale

Controllers coordinate requests.

Repositories manage persistence.

Services encapsulate business behavior, validation, and transactional boundaries.

---

# ADR-014 — Query Optimization Before Scaling

## Decision

Optimize queries using projections, aggregations, and batch loading before introducing distributed infrastructure.

## Rationale

Efficient query design provides the greatest performance gains with the least operational complexity.

---

# ADR-015 — Avoid Premature Optimization

## Decision

Scale-driven enhancements such as partitioning, A/B testing infrastructure, and advanced recommendation strategies are intentionally deferred.

## Rationale

Complexity should be introduced only when justified by measurable application growth.

---

## Guiding Principle

Every architectural decision in PricePilot is evaluated against three questions:

1. Does it improve maintainability?
2. Does it improve correctness or performance without unnecessary complexity?
3. Will it make future AI and cloud integration easier?

If the answer to these questions is **yes**, the change is adopted. Otherwise, it is deferred until justified by real-world requirements.