# PricePilot Maintenance Phase M4: Observability & Operational Excellence

This document provides a comprehensive operational guide, metric dictionary, Prometheus scrape configuration, Grafana dashboard specifications, and recommended alert thresholds for PricePilot.

---

## 1. System Observability Overview

PricePilot enforces production-grade observability across all architectural tiers:

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 Prometheus Scraper                     │
                  └───────┬────────────────────────┬───────────────────────┘
                          │ /actuator/prometheus   │ /metrics
                          ▼                        ▼
                ┌──────────────────┐     ┌──────────────────┐
                │ PricePilot       │     │ PricePilot AI    │
                │ Spring Boot      │────►│ FastAPI          │
                │ (Micrometer)     │     │ (Prometheus)     │
                └─────────┬────────┘     └──────────────────┘
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
  ┌──────────────────┐        ┌──────────────────┐
  │ PostgreSQL 15    │        │ Redis 7          │
  │ (HikariCP/pg_stat)│       │ (Cache Metrics)  │
  └──────────────────┘        └──────────────────┘
```

---

## 2. Spring Boot Micrometer & Actuator Audit

### Actuator Endpoint Mapping & Security
| Endpoint | Path | Access Control | Purpose |
|---|---|---|---|
| Health Overall | `/actuator/health` | Public (`permitAll`) | System status & component health |
| Liveness Probe | `/actuator/health/liveness` | Public (`permitAll`) | Kubernetes / Docker Liveness check |
| Readiness Probe | `/actuator/health/readiness` | Public (`permitAll`) | Container readiness check |
| Prometheus Scraping | `/actuator/prometheus` | Public (`permitAll`) | Prometheus metrics scraping |
| Application Info | `/actuator/info` | Public (`permitAll`) | Build version & app metadata |
| Detailed Metrics | `/actuator/metrics` | Admin Only (`hasRole('ADMIN')`) | Ad-hoc metric introspection |
| Env / Beans / Heap | `/actuator/env`, `/actuator/beans`, etc. | Admin Only (`hasRole('ADMIN')`) | Sensitive runtime diagnostics |

### Micrometer Metric Categories
1. **JVM Metrics**:
   - `jvm.memory.used`, `jvm.memory.max` (Heap & Non-heap)
   - `jvm.gc.pause` (GC pause duration & frequency)
   - `jvm.threads.live`, `jvm.threads.peak` (Thread counts)
   - `system.cpu.usage`, `process.cpu.usage` (CPU utilization)
   - `jvm.classes.loaded` (Class loading)
2. **HikariCP Connection Pool**:
   - `hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending`, `hikaricp.connections.max`
3. **HTTP Server**:
   - `http.server.requests` (Tags: `uri`, `method`, `status`, `outcome`, `exception`)
4. **HTTP Client (Downstream to FastAPI)**:
   - `http.client.requests` (Tags: `uri`, `method`, `status`, `clientName`)
5. **Executor / Task Pools**:
   - `executor.completed`, `executor.active`, `executor.queued`, `executor.pool.size`
6. **Cache & Redis**:
   - `pricepilot.cache.hits`, `pricepilot.cache.misses` (Tagged by `cache` name)
   - `cache.gets`, `cache.puts`, `cache.evictions`
7. **Database & Flyway**:
   - `flyway.migrations`
   - `hibernate.sessions.open`, `hibernate.queries.executing`, `hibernate.statements`

---

## 3. FastApi AI Service Observability

- **Metrics Endpoint**: `/metrics` (Prometheus text format)
- **Key Metrics Exposed**:
  - `pricepilot_ai_requests_total{endpoint, algorithm, status}`: Request count counter.
  - `pricepilot_ai_inference_duration_seconds{endpoint, algorithm}`: Inference latency histogram.
  - `http_request_duration_seconds`: Middleware request duration histogram.
- **Request Tracing & Headers**:
  - `X-Request-ID`: Generated or received correlation ID propagated across all HTTP responses.
  - `X-Response-Time`: Exact request execution duration header.
- **Structured Logs**:
  - JSON format with startup metadata (`api_key_status`, `loaded_models`, `container_limit_mb`), HTTP request context, rate limit violations, and model inference results.

---

## 4. Distributed Tracing & MDC Correlation Strategy

- **Spring Boot Filter**: `CorrelationIdFilter` intercepts every incoming HTTP request.
  - Extracts or generates `X-Request-ID` / `X-Correlation-ID`.
  - Sets MDC context keys: `requestId`, `correlationId`, `clientIp`, `userId`.
  - Injects response headers: `X-Request-ID`, `X-Correlation-ID`.
- **Downstream HTTP Propagation**:
  - `AiClientImpl` forwards `X-Request-ID` and `X-Correlation-ID` headers to FastAPI AI Service.
- **Global Exception Handler Logging**:
  - `GlobalExceptionHandler` logs all handled exceptions with `request_id`, `endpoint`, `user`, `status`, `root_exception`, and stack trace (for 5xx).

---

## 5. PostgreSQL & Redis Runtime Observability

### PostgreSQL
- **HikariCP Pool**: Max pool size = 20, Min idle = 5.
- **Indexes**: Indexed on `products(category)`, `products(brand)`, `product_prices(product_id, seller_id)`, `price_history(product_id, changed_at)`, `saved_products(user_id, product_id)`, `price_watchlists(user_id, product_id)`.
- **Query Diagnostics**: `pg_stat_statements` enabled for slow query identification, deadlock detection (`pg_stat_database`), and buffer cache hit ratio tracking.

### Redis
- **Eviction Policy**: `allkeys-lru` with memory limit configured.
- **Cache Namespaces**:
  - `product-details` (TTL: 30 min)
  - `product-searches` (TTL: 5 min)
  - `popular-products`, `trending-products`, `most-watched-products`, `most-saved-products`, `biggest-drops` (TTL: 15-60 min)
  - `recommendations`, `dashboard` (TTL: 5-10 min)
- **Statistics**: `spring.cache.redis.enable-statistics=true` exposes hit/miss ratios per namespace.

---

## 6. Frontend Production Diagnostics

- **Source Map Strategy**: `build.sourcemap: 'hidden'` generates `.map` files for error symbolication without exposing raw source URLs in production.
- **Error Boundary**: `<ErrorBoundary>` catches uncaught rendering crashes, displays user-friendly recovery UI, and logs error details.
- **React Query Devtools**: Excluded from production builds.
- **Code Splitting & Bundle Analysis**: `manualChunks` splits vendor bundles (`vendor-react`, `vendor-query`, `vendor-ui`, `vendor-utils`).
- **Core Web Vitals**: `initWebVitals()` listens for TTFB, FCP, LCP, CLS, and DOM load timings.
- **API Latency Logging**: Request/Response interceptor logs API call duration in development (`import.meta.env.DEV`).

---

## 7. Grafana Dashboard Readiness & PromQL Queries

| Dashboard Category | Recommended Panel | Metric / PromQL Query |
|---|---|---|
| **API Performance** | HTTP Request Rate (RPS) | `sum(rate(http_server_requests_seconds_count[5m])) by (uri, status)` |
| **API Performance** | 95th Percentile Latency | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` |
| **API Performance** | HTTP 5xx Error Rate | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100` |
| **JVM Health** | Heap Memory Usage % | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100` |
| **JVM Health** | GC Pause Duration | `sum(rate(jvm_gc_pause_seconds_sum[5m]))` |
| **JVM Health** | Live Threads | `jvm_threads_live_threads` |
| **Hikari Connection Pool** | Active Connections | `hikaricp_connections_active` |
| **Hikari Connection Pool** | Pending Threads | `hikaricp_connections_pending` |
| **Redis Cache** | Cache Hit Ratio | `sum(rate(pricepilot_cache_hits_total[5m])) / (sum(rate(pricepilot_cache_hits_total[5m])) + sum(rate(pricepilot_cache_misses_total[5m]))) * 100` |
| **AI Service** | Inference Latency (P95) | `histogram_quantile(0.95, sum(rate(pricepilot_ai_inference_duration_seconds_bucket[5m])) by (le, algorithm))` |
| **AI Service** | AI Request Rate | `sum(rate(pricepilot_ai_requests_total[5m])) by (algorithm, status)` |

---

## 8. Recommended Alert Thresholds

```yaml
groups:
  - name: PricePilotAlerts
    rules:
      # Backend Alerts
      - alert: BackendJVMHeapHigh
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100 > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM Heap usage exceeded 85%"

      - alert: HikariPoolExhaustion
        expr: hikaricp_connections_active / hikaricp_connections_max * 100 > 90
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "HikariCP connection pool usage > 90%"

      - alert: BackendHigh5xxRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) > 5
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Spike in HTTP 5xx errors on backend"

      - alert: HighApiLatency
        expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 2.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "95th percentile API response time > 2 seconds"

      # Redis Alerts
      - alert: RedisMemoryHigh
        expr: redis_memory_used_bytes / redis_memory_max_bytes * 100 > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis memory usage > 80%"

      - alert: RedisCacheHitRatioLow
        expr: sum(rate(pricepilot_cache_hits_total[5m])) / (sum(rate(pricepilot_cache_hits_total[5m])) + sum(rate(pricepilot_cache_misses_total[5m]))) * 100 < 80
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Redis cache hit ratio dropped below 80%"

      # AI Service Alerts
      - alert: AiInferenceLatencyHigh
        expr: histogram_quantile(0.95, sum(rate(pricepilot_ai_inference_duration_seconds_bucket[5m])) by (le)) > 1.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "AI service inference latency > 1.5 seconds"

      - alert: AiServiceDown
        expr: up{job="pricepilot-ai"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "FastAPI AI service is unreachable"
```
