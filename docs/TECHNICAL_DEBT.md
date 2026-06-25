# PricePilot Technical Debt Review

This document contains the Technical Debt Review and Production Readiness classification for the PricePilot system before starting Phase 3 (Python + Machine Learning).

---

## Technical Debt Classification

| # | Technical Debt Item | Category | Status | Complexity |
|---|---|---|---|---|
| 1 | Add integration tests for Saved Products | Category A — Implement Now | Implemented | Low |
| 2 | Consider projection for Saved Products list | Category B — Design for Later | Future | Medium |
| 3 | Introduce domain-specific exceptions | Category A — Implement Now | Implemented | Low |
| 4 | Revisit UserPrincipal optimization | Category A — Implement Now | Implemented | Medium |
| 5 | Monitor Saved Product query count after analytics | Category B — Design for Later | Future | Low |
| 6 | Reduce queries during Watchlist creation | Category A — Implement Now | Implemented | Medium |
| 7 | Implement unique view tracking | Category C — Defer | Deferred | Medium |
| 8 | Add time-decay trending algorithm | Category B — Design for Later | Future | Medium |
| 9 | Evaluate Redis caching for trending endpoints | Category B — Design for Later | Future | Low |
| 10| Introduce user interaction event tracking improvements | Category B — Design for Later | Future | Medium |
| 11| Create integration tests for analytics workflows | Category B — Design for Later | Future | Medium |
| 12| Add retention strategy for interaction events | Category B — Design for Later | Future | Low |
| 13| Investigate PostgreSQL table partitioning | Category C — Defer | Deferred | High |
| 14| Define metadata schema contract per interaction type | Category B — Design for Later | Future | Medium |
| 15| Add negative interaction events (dismiss/bounce) | Category C — Defer | Deferred | Medium |
| 16| Distinguish anonymous vs authenticated identities | Category C — Defer | Deferred | Medium |
| 17| Add end-to-end event integration tests | Category B — Design for Later | Future | Medium |
| 18| Replace in-memory recommendation slicing with DB ranking | Category B — Design for Later | Future | Medium |
| 19| Introduce recommendation diversity/exploration strategy | Category B — Design for Later | Future | Medium |
| 20| Batch PRODUCT_VIEW cache invalidation | Category B — Design for Later | Future | Medium |
| 21| Add A/B testing support for recommendation algorithms | Category C — Defer | Deferred | High |
| 22| Add recommendation explanation metadata | Category B — Design for Later | Future | Low |

---

## Detailed Reviews & Implementation Strategy

### 1. Add integration tests for Saved Products
*   **Category:** Category A — Implement Now
*   **Status:** Implemented
*   **Reason:** Unit tests verify mocked behavior, but integration tests verify real JPA query behaviors, Flyway migrations, database constraints, and caching effects.
*   **Expected benefits:** High reliability, early detection of database schema mismatches, prevents regressions.
*   **Potential trade-offs:** Increases test build execution time slightly.
*   **Estimated complexity:** Low
*   **When it should be revisited:** Completed.

### 2. Consider projection for Saved Products list
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Currently, saved products are fetched using a fetch join `findAllByUserIdWithProduct`, which prevents N+1 queries. Custom projections (e.g., Spring Data projections) can be designed later when the dataset grows and we need to minimize data transfer.
*   **Expected benefits:** Reduced memory footprint and database network payload.
*   **Potential trade-offs:** Introduces projection interfaces or DTO queries, adding minor boilerplates.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** When pagination is introduced for saved products, or when median saved products per user exceeds 100.

### 3. Introduce domain-specific exceptions
*   **Category:** Category A — Implement Now
*   **Status:** Implemented
*   **Reason:** Replacing generic `IllegalArgumentException` with specific business exceptions improves system design, readability, error categorization, and allows cleaner handling in `GlobalExceptionHandler`.
*   **Expected benefits:** Accurate error responses (e.g. distinguishing 400 Bad Request sub-types like `ProductArchivedException`, `InvalidPriceException` or `InvalidWatchlistPriceException`).
*   **Potential trade-offs:** Creates extra exception classes.
*   **Estimated complexity:** Low
*   **When it should be revisited:** Completed.

### 4. Revisit UserPrincipal optimization
*   **Category:** Category A — Implement Now
*   **Status:** Implemented
*   **Reason:** Currently, the JWT filter loads user details from the database on every authenticated API request. Encapsulating user ID and roles within JWT claims allows us to reconstruct the `UserPrincipal` directly without database roundtrips.
*   **Expected benefits:** Eliminates 1 SQL query per authenticated request, significantly improving API performance and scalability.
*   **Potential trade-offs:** Invalidation of active sessions (JWTs) requires blacklist/caching logic if roles change, but this is a standard trade-off.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** Completed.

### 5. Monitor Saved Product query count after analytics
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Hibernate statistics are already enabled in dev to audit queries. Putting formal alerts or automated query metrics trackers in place is best suited for deployment/CI phases.
*   **Expected benefits:** Proactive detection of query count regressions or N+1 queries.
*   **Potential trade-offs:** Requires APM instrumentation or log parser configuration.
*   **Estimated complexity:** Low
*   **When it should be revisited:** Prior to staging deployment.

### 6. Reduce queries during Watchlist creation
*   **Category:** Category A — Implement Now
*   **Status:** Implemented
*   **Reason:** Watchlist creation originally executed 5 queries: find user, find product, check duplicate, find best price, and insert. By utilizing the `UserPrincipal` context and a combined `findProductAndBestPrice` query, we reduce this footprint.
*   **Expected benefits:** Reduces write latency, decreases database connection pool usage under load.
*   **Potential trade-offs:** The repository returns raw `Object[]` instead of entities, which requires mapping in the service.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** Completed.

### 7. Implement unique view tracking
*   **Category:** Category C — Defer
*   **Status:** Deferred
*   **Reason:** The system only requires raw view counts for global trending heuristics. Deduplicating views (e.g. by IP/session) is unnecessary overhead at this stage.
*   **Expected benefits:** Prevents metric manipulation (view spamming).
*   **Potential trade-offs:** Requires IP/cookie storage, increases write workload on Redis/DB.
*   **Estimated complexity:** Medium
*   **When it should be revisited/Trigger conditions:** Revisit if user analytics reveal high view manipulation or spamming.

### 8. Add time-decay trending algorithm
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Currently, trending is computed using basic arithmetic weights. A time-decay algorithm (e.g. half-life exponential decay) should be designed alongside ML recommender systems in Phase 3.
*   **Expected benefits:** More dynamic and relevant trending lists that prioritize new popular products over historical mainstays.
*   **Potential trade-offs:** Computationally heavier; requires scheduled batch jobs or custom DB functions.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** During Phase 3 (Machine Learning recommendations integration).

### 9. Evaluate Redis caching for trending endpoints
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** At low traffic, querying indexed tables directly is sufficient. Redis caching should be added when read traffic on trending lists dominates system resources.
*   **Expected benefits:** Sub-millisecond response times for home/trending feeds.
*   **Potential trade-offs:** Introduces cache invalidation complexity.
*   **Estimated complexity:** Low
*   **When it should be revisited:** When concurrent users exceed 1,000.

### 10. Introduce user interaction event tracking improvements
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** The current event tracking logic captures core activities (save, view, watchlist). Refining the track logic or introducing messaging queues can wait until events are actively consumed by the ML pipeline.
*   **Expected benefits:** Cleaner event pipelines, decoupling API requests from tracking.
*   **Potential trade-offs:** Complexity of queue broker (e.g. RabbitMQ/Kafka).
*   **Estimated complexity:** Medium
*   **When it should be revisited:** As part of Phase 3 pipeline setup.

### 11. Create integration tests for analytics workflows
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Analytics counters are incremented synchronously in the current scope. Integration tests can be added later when asynchronous queues are introduced.
*   **Expected benefits:** Ensures accurate view/save metric aggregation under concurrent load.
*   **Potential trade-offs:** Requires concurrent testing tools and setup.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** When moving analytics updates to an async queue/scheduler.

### 12. Add retention strategy for interaction events
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** The interaction events table will grow linearly. A retention/TTL strategy is not needed locally but will be vital in production to prevent disk bloat.
*   **Expected benefits:** Bounds database growth and keeps index sizes small.
*   **Potential trade-offs:** Historical data is lost unless archived to a data lake.
*   **Estimated complexity:** Low
*   **When it should be revisited:** Prior to launch.

### 13. Investigate PostgreSQL table partitioning
*   **Category:** Category C — Defer
*   **Status:** Deferred
*   **Reason:** Partitioning is only beneficial when tables hold tens of millions of rows. Currently, table sizes are negligible.
*   **Expected benefits:** Faster index lookups and easier archival of old partitions.
*   **Potential trade-offs:** Complexity in schema migrations and JPA configuration.
*   **Estimated complexity:** High
*   **When it should be revisited/Trigger conditions:** Revisit when the `user_interaction_events` table grows past 10 million rows.

### 14. Define metadata schema contract per interaction type
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Using unstructured JSONB allows fast prototyping. We can enforce structured contracts (e.g., JSON schema) when stable event payload definitions are settled in Phase 3.
*   **Expected benefits:** Prevents malformed metadata from corrupting downstream ML pipelines.
*   **Potential trade-offs:** Limits metadata flexibility during development.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** When building the ETL ingestion pipeline for Phase 3.

### 15. Add negative interaction events (dismiss, bounce, ignored)
*   **Category:** Category C — Defer
*   **Status:** Deferred
*   **Reason:** The heuristic-based recommendation engine does not utilize negative feedback signals. Implementing this now is speculative.
*   **Expected benefits:** Captures richer user behavior for collaborative filtering algorithms.
*   **Potential trade-offs:** High client-side complexity to track dismiss/bounce events.
*   **Estimated complexity:** Medium
*   **When it should be revisited/Trigger conditions:** Revisit when training ML models that require negative feedback targets.

### 16. Distinguish anonymous vs authenticated identities
*   **Category:** Category C — Defer
*   **Status:** Deferred
*   **Reason:** PricePilot is currently designed for authenticated users only. Personalized recommendations require active user profiles.
*   **Expected benefits:** Allows tracking guest user browsing history for cold-start recommendations.
*   **Potential trade-offs:** High tracking complexity, cookie management, session-merging logic.
*   **Estimated complexity:** Medium
*   **When it should be revisited/Trigger conditions:** If the product team decides to enable guest watchlist/browse features.

### 17. Add end-to-end event integration tests
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Service and unit tests are currently verifying event tracking correctness. Full end-to-end integration tests are deferred until API structures are finalized.
*   **Expected benefits:** Verifies the full REST-to-event workflow.
*   **Potential trade-offs:** Increases test suite runtime.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** Prior to establishing production CI/CD pipelines.

### 18. Replace in-memory recommendation slicing with database-assisted ranking
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Currently, the catalog is small, so candidate evaluation in memory is fast. When the catalog expands to thousands of items, DB-assisted scoring (SQL ranking or Vector search) will be necessary.
*   **Expected benefits:** Reduces JVM memory load and network traffic for large catalogs.
*   **Potential trade-offs:** Harder to express complex heuristic equations in standard SQL.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** When the product catalog exceeds 10,000 active products.

### 19. Introduce recommendation diversity / exploration strategy
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Current algorithms focus on direct matches. Implementing diversity constraints (e.g. category ceilings, collaborative filtering exploration) is an ML refinement task for Phase 3.
*   **Expected benefits:** Prevents user bubble effect, increases click-through rates.
*   **Potential trade-offs:** May lower immediate relevancy scores.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** During Phase 3 recommendation tuning.

### 20. Batch PRODUCT_VIEW cache invalidation
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Low-frequency page views do not cause cache churn. Batching invalidations will be useful under heavy traffic to prevent Redis thrashing.
*   **Expected benefits:** Stable cache hit ratios and reduced Redis write commands.
*   **Potential trade-offs:** Analytics view numbers on screen may be slightly delayed.
*   **Estimated complexity:** Medium
*   **When it should be revisited:** When write traffic on views exceeds 50 reqs/sec.

### 21. Add A/B testing support for recommendation algorithms
*   **Category:** Category C — Defer
*   **Status:** Deferred
*   **Reason:** PricePilot does not have traffic or alternative models to A/B test yet.
*   **Expected benefits:** Enables data-driven comparisons of new algorithms.
*   **Potential trade-offs:** Requires traffic routing, variant assignment persistence, and tracking variant IDs in analytics.
*   **Estimated complexity:** High
*   **When it should be revisited/Trigger conditions:** After launching the first ML recommendation model in production and having sufficient daily active users.

### 22. Add recommendation explanation metadata
*   **Category:** Category B — Design for Later
*   **Status:** Future
*   **Reason:** Explanations (e.g. "Because you watched X") are highly beneficial for UX, but should be designed when Phase 3 models are formulated.
*   **Expected benefits:** Increased user trust and engagement.
*   **Potential trade-offs:** Requires returning explanation tokens alongside recommendation DTOs.
*   **Estimated complexity:** Low
*   **When it should be revisited:** During Phase 3 frontend redesign.
