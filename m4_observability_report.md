# PricePilot Maintenance Phase M4: Observability & Operational Excellence Report

## Executive Summary

Phase M4 of PricePilot focused strictly on **Observability, Diagnostics, Structured Logging, Metrics, and Operational Readiness** across all system components without introducing functional changes, new endpoints, or altering business logic.

All architectural tiers—Spring Boot backend, FastAPI AI microservice, React frontend, PostgreSQL database, Redis caching layer, Python SDK, and Docker container stack—were audited, configured, and verified. 

Key achievements:
- **Backend Observability**: Actuator endpoints mapped and secured; full Micrometer metrics exposure via `/actuator/prometheus`.
- **Request Correlation & MDC**: End-to-end request tracing via `X-Request-ID` and `X-Correlation-ID` across Controller -> Service -> Repository -> AI Microservice -> Response Headers & CORS.
- **Exception Observability**: Upgraded global exception handler to log timestamp, request ID, HTTP method, endpoint URI, status code, user ID, execution duration (`duration_ms`), cause chain, and root exception.
- **FastAPI AI Service**: Structured JSON logging, startup diagnostics, model load timing, inference duration histograms, and health/liveness/readiness probes.
- **Frontend Diagnostics**: React Error Boundaries, TanStack Query production caching, hidden source map generation (`build.sourcemap: 'hidden'`), Core Web Vitals instrumentation, and exclusion of dev tools in production builds.
- **Docker & Infrastructure Readiness**: Health checks, restart policies (`unless-stopped`), resource limits, graceful shutdown grace periods, and Prometheus/Grafana alert readiness.
- **Automated Verification**: 100% test suite pass rate across Backend (74 tests), AI Service (11 tests), Python SDK (41 tests), and Frontend (14 vitest tests + production build + 0 npm vulnerabilities).

---

## Backend Observability

The Spring Boot backend utilizes Spring Boot Actuator and Micrometer to expose runtime diagnostic metrics.

### Actuator Endpoint Audit & Security Policy
| Endpoint | Path | Access Control | Purpose |
|---|---|---|---|
| Health Overall | `/actuator/health` | Public (`permitAll`) | Top-level system status |
| Liveness Probe | `/actuator/health/liveness` | Public (`permitAll`) | Kubernetes / Docker liveness check |
| Readiness Probe | `/actuator/health/readiness` | Public (`permitAll`) | Container readiness check |
| Prometheus Scraping | `/actuator/prometheus` | Public (`permitAll`) | Prometheus metrics scraping endpoint |
| Application Info | `/actuator/info` | Public (`permitAll`) | Version and application metadata |
| Detailed Metrics | `/actuator/metrics` | Admin Only (`hasRole('ADMIN')`) | Ad-hoc metric introspection |
| Env / Beans / Heap | `/actuator/env`, `/actuator/beans`, etc. | Admin Only (`hasRole('ADMIN')`) | Sensitive runtime configuration |

---

## Logging Audit

### Structured Logging & Configuration
- **Production Format**: ECS-compliant JSON logging enabled via `logging.structured.format.console=ecs` in `application-prod.properties`.
- **Log Levels**: Standardized to `INFO` for application logs (`com.pricepilot`), `ERROR` for SQL bindings/Hibernate queries (`org.hibernate.SQL=ERROR`).
- **Sensitive Data Scrubbing**: Audited all logger calls. Confirmed zero logging of raw passwords, JWT tokens, API keys, or personal identifiable information (PII).

### Startup & Shutdown Diagnostics
- **Startup**: `StartupDiagnostics` logs JVM parameters, active Spring profile (`dev`/`prod`), database URL, and Redis host on startup.
- **Shutdown**: Graceful shutdown enabled (`server.shutdown=graceful`) with a `30s` timeout per phase.

---

## Metrics Inventory

The backend and AI microservice collect and export the following metrics:

### 1. JVM & System Metrics
- `jvm.memory.used`, `jvm.memory.max` (Heap and non-heap memory areas)
- `jvm.gc.pause` (GC pause frequency and duration)
- `jvm.threads.live`, `jvm.threads.peak`, `jvm.threads.daemon` (Thread threadpool state)
- `system.cpu.usage`, `process.cpu.usage` (System and process CPU utilization)
- `jvm.classes.loaded`, `jvm.classes.unloaded` (Classloader stats)

### 2. Connection Pool (HikariCP)
- `hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending`, `hikaricp.connections.max`
- `hikaricp.connections.acquire`, `hikaricp.connections.creation`

### 3. HTTP Server & Downstream Client Metrics
- `http.server.requests` (Auto-timed timer tagged by `uri`, `method`, `status`, `outcome`, `exception`)
- `http.client.requests` (Downstream RestTemplate calls to FastAPI AI Service)

### 4. Cache & Database Metrics
- `spring.cache.redis.enable-statistics=true` exposes hit/miss ratios for all Redis cache namespaces.
- `hibernate.sessions.open`, `hibernate.queries.executing`, `hibernate.statements` enabled via `spring.jpa.properties.hibernate.generate_statistics=true`.
- `flyway.migrations` tracks applied database migration scripts.

---

## Actuator Audit

All Actuator endpoints were verified against security guidelines. Publicly accessible endpoints are limited to non-sensitive health probes (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`), build info (`/actuator/info`), and Prometheus metrics scraping (`/actuator/prometheus`). All internal state endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/metrics/*`) require authenticated `ADMIN` role access.

---

## Request Correlation

Request correlation is enforced end-to-end:
1. **HTTP Ingress**: `CorrelationIdFilter` intercepts incoming requests, extracting or generating `X-Request-ID` and `X-Correlation-ID` UUIDs.
2. **Context Storage**: MDC keys `requestId`, `correlationId`, `clientIp` are populated.
3. **Authentication Context**: `JwtAuthenticationFilter` populates `userId` into MDC upon successful token verification.
4. **Downstream Egress**: `AiClientImpl` RestTemplate interceptor automatically forwards `X-Request-ID` and `X-Correlation-ID` to the FastAPI AI service.
5. **Egress Headers**: `CorrelationIdFilter` attaches `X-Request-ID` and `X-Correlation-ID` to outgoing HTTP response headers.
6. **CORS Headers**: `SecurityConfig` includes `X-Request-ID` and `X-Correlation-ID` in `allowedHeaders` and `exposedHeaders`.

---

## Exception Observability

The `GlobalExceptionHandler` was audited and updated to log complete diagnostic context for all handled exceptions:
- **Timestamp**: ISO UTC timestamp.
- **Request ID**: `requestId` from MDC or request header.
- **HTTP Method**: `request.getMethod()`.
- **Endpoint**: `request.getRequestURI()`.
- **Status Code**: HTTP status integer (e.g., `400`, `404`, `409`, `500`).
- **User**: Authenticated user ID or `anonymous`.
- **Execution Duration**: `duration_ms` calculated from request arrival timestamp.
- **Cause Chain**: `ex.getClass().getSimpleName() -> cause.getClass().getSimpleName()`.
- **Root Exception**: Class name and root message.

---

## PostgreSQL Runtime Observability

PostgreSQL 15 configuration and diagnostic queries for operational inspection:

### Diagnostic Queries
```sql
-- 1. Active Queries & Execution Status
SELECT pid, usename, client_addr, state, query_start, now() - query_start AS duration, query
FROM pg_stat_activity
WHERE state != 'idle' ORDER BY duration DESC;

-- 2. Slow Queries (pg_stat_statements)
SELECT query, calls, total_exec_time, min_exec_time, max_exec_time, mean_exec_time, rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC LIMIT 10;

-- 3. Lock Dependencies & Deadlocks
SELECT blocked_locks.pid AS blocked_pid, blocked_activity.usename AS blocked_user,
       blocking_locks.pid AS blocking_pid, blocking_activity.usename AS blocking_user,
       blocked_activity.query AS blocked_statement
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks 
  ON blocking_locks.locktype = blocked_locks.locktype
 AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
 AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
 AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;

-- 4. Buffer Cache Hit Ratio
SELECT sum(heap_blks_read) as heap_read,
       sum(heap_blks_hit)  as heap_hit,
       sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) * 100 AS ratio
FROM pg_statio_user_tables;

-- 5. Sequential Scans vs Index Scans
SELECT relname, seq_scan, seq_tup_read, idx_scan, idx_tup_fetch
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;
```

---

## Redis Runtime Observability

Redis 7 configuration and metric diagnostic checks:

### Configuration Verification
- **Eviction Policy**: `allkeys-lru`
- **Memory Limit**: Configured via `--maxmemory` parameter in `docker-compose.yml`.
- **Statistics**: Enabled via `spring.cache.redis.enable-statistics=true`.

### Redis Diagnostic Commands
- `INFO memory`: Inspect `used_memory`, `used_memory_peak`, `mem_fragmentation_ratio`.
- `INFO stats`: Measure `keyspace_hits`, `keyspace_misses`, `evicted_keys`, `expired_keys`.
- `INFO keyspace`: Monitor active keys per database index.

---

## AI Service Observability

FastAPI AI Service (`pricepilot-ai`) observability verification:
- **Structured JSON Logging**: Implemented via `log_structured()` with ISO UTC timestamps.
- **Request Tracing**: `add_observability_headers` middleware extracts/generates `X-Request-ID` and records `X-Response-Time`.
- **Startup Diagnostics**: `lifespan` handler logs environment, model directory, dataset directory, API key status (`CONFIGURED` / `INSECURE_DEFAULT`), container memory limits, and total system memory.
- **Model Load Duration**: `ModelRegistry` records `load_duration_seconds` during startup and hot reloads.
- **Prometheus Metrics**:
  - Counter: `pricepilot_ai_requests_total{endpoint, algorithm, status}`
  - Histogram: `pricepilot_ai_inference_duration_seconds{endpoint, algorithm}`
- **Health Probes**: `/health`, `/health/liveness`, `/health/readiness`, `/metrics`.

---

## Frontend Diagnostics

React Frontend (`frontend`) production diagnostic suite:
- **Error Boundaries**: Root `<ErrorBoundary>` component catches unhandled rendering errors and displays standard fallback UI.
- **React Query Config**: `staleTime: 5 mins`, `gcTime: 15 mins`, `refetchOnWindowFocus: false`, `retry: 1`. React Query Devtools excluded from production build.
- **Source Map Strategy**: `sourcemap: 'hidden'` configured in `vite.config.ts` (generates `.map` files for symbolication without exposing raw source URLs in production JS bundles).
- **Code Splitting**: Route components lazy loaded via React `lazy()` and `Suspense`; manual chunking configured for `vendor-react`, `vendor-query`, `vendor-ui`, `vendor-utils`.
- **Core Web Vitals**: `initWebVitals()` listens for TTFB, FCP, DomInteractive, DomComplete timings.
- **API Latency Logging**: Request/Response interceptors log duration conditionally in development (`import.meta.env.DEV`).

---

## Docker Operational Readiness

### Container Configurations (`docker-compose.yml`)
- **Health Checks**:
  - `pricepilot-db`: `pg_isready -U postgres -d pricepilot` (Interval: 10s, Timeout: 5s, Retries: 5)
  - `pricepilot-redis`: `redis-cli ping` (Interval: 10s, Timeout: 5s, Retries: 5)
  - `pricepilot-ai`: `curl -f http://localhost:8000/health/readiness` (Interval: 10s, Timeout: 5s, Retries: 5)
  - `pricepilot-backend`: `curl -f http://localhost:8080/actuator/health/readiness` (Interval: 15s, Timeout: 5s, Retries: 3)
  - `pricepilot-frontend`: `wget --spider -q http://127.0.0.1:8080/` (Interval: 30s, Timeout: 5s, Retries: 3)
- **Restart Policy**: `restart: unless-stopped` on all services.
- **Graceful Shutdown**:
  - DB & Redis: `15s`
  - AI Service: `30s`
  - Backend: `45s`
  - Frontend: `15s`
- **Startup Order**: Backend depends on DB, Redis, and AI services with `condition: service_healthy`. Frontend depends on Backend with `condition: service_healthy`.
- **Resource Limits**:
  - DB: Limit 1.0 CPU, 512MB RAM | Reservation 0.25 CPU, 256MB RAM
  - Redis: Limit 0.5 CPU, 256MB RAM | Reservation 0.1 CPU, 128MB RAM
  - AI: Limit 1.5 CPU, 1024MB RAM | Reservation 0.5 CPU, 512MB RAM
  - Backend: Limit 2.0 CPU, 1024MB RAM | Reservation 0.5 CPU, 512MB RAM
  - Frontend: Limit 0.5 CPU, 128MB RAM | Reservation 0.1 CPU, 64MB RAM

---

## Prometheus Readiness

Scrape configuration (`prometheus/prometheus.yml`) is verified for scrapability:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'pricepilot-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['pricepilot-backend:8080']

  - job_name: 'pricepilot-ai'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['pricepilot-ai:8000']
```

---

## Grafana Readiness

Verified metric availability for key dashboard categories:
- **API Performance**: `http_server_requests_seconds_count`, `http_server_requests_seconds_bucket`
- **JVM Health**: `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_sum`, `jvm_threads_live_threads`
- **Hikari Connection Pool**: `hikaricp_connections_active`, `hikaricp_connections_pending`
- **Redis Cache**: `pricepilot_cache_hits_total`, `pricepilot_cache_misses_total`
- **AI Service**: `pricepilot_ai_requests_total`, `pricepilot_ai_inference_duration_seconds_bucket`

---

## Alert Recommendations

Production Alert Rules (`prometheus/alerts.yml`):

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

---

## Remaining Technical Debt

1. **Centralized Log Aggregation**: Logs currently output to standard output (`json-file` log driver). Integrating Loki or ELK in production will simplify multi-container query aggregation.
2. **OpenTelemetry Distributed Tracing**: Request IDs are propagated via headers and MDC; upgrading to OpenTelemetry W3C trace contexts (`traceparent`) will allow seamless integration with Tempo/Jaeger.

---

## Risks

1. **High Ingestion Cardinality**: Unbounded high-cardinality URIs in HTTP server metrics could increase Prometheus memory consumption. Standardized request mappings mitigate this risk.
2. **Redis Memory Limits**: LRU eviction policies prevent OOM, but monitoring key expiration rates remains essential during traffic spikes.

---

## Production Readiness Assessment

- **Backend**: **PASSED** (74/74 tests passed, Actuator secured, correlation headers active, exception context complete).
- **FastAPI AI**: **PASSED** (11/11 tests passed, metrics registered, model loading timed, health endpoints verified).
- **Frontend**: **PASSED** (14/14 tests passed, prod build verified, hidden sourcemaps configured, 0 vulnerabilities).
- **Python SDK**: **PASSED** (41/41 tests passed).
- **Docker Stack**: **PASSED** (Health checks, restart policies, resource limits, and shutdown timeouts configured).

---

## Final Verdict

**PHASE M4 IS COMPLETE AND PASSED.**

All acceptance criteria for PricePilot Maintenance Phase M4 (Observability & Operational Excellence) have been verified and documented.
