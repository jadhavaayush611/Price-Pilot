# PricePilot v1.0.1

Release date: 2026-07-22

## Overview

PricePilot v1.0.1 is a dedicated maintenance and production-readiness release focused on hardening core infrastructure, improving runtime resilience, and establishing comprehensive observability across all microservices. This release introduces no new user-facing features or breaking changes, concentrating entirely on stability, security, performance, caching, observability, resilience, and code maintainability to ensure PricePilot delivers enterprise-grade reliability in production environments.

Key focus areas in this release include:
- **Security**: Hardened cache deserialization controls and HTTP security response headers.
- **Performance & Caching**: Streamlined database query patterns and optimized Redis caching strategies.
- **Observability**: Standardized structured JSON logging, distributed trace context propagation, custom metrics, and Grafana dashboards.
- **Resilience**: Implemented circuit breaker fault tolerance, distributed rate limiting, and graceful container shutdown hooks.
- **Maintainability**: Unified cross-repository versioning, fixed strict static analysis rules, and cleaned obsolete technical debt.

---

## Highlights

### Security Hardening
- **Redis Deserialization Shielding**: Enforced explicit package allowlists for Redis cache deserialization to prevent arbitrary code execution vectors.
- **HTTP Header Hardening**: Standardized enterprise HTTP security headers (`Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Permissions-Policy`, and `Referrer-Policy`) across all backend API responses.

### Performance & Redis Optimization
- **Database Query Footprint Reduction**: Reduced database queries during watchlist creation from 5 roundtrips down to 2 using targeted JPQL fetch joins.
- **Stateless Claim Extraction**: Reconstructed authenticated user principals directly from validated JWT claims, eliminating redundant database lookups per request.
- **Cache Key & Payload Tuning**: Streamlined Redis key namespaces and minimized cached search result payload sizes to maximize cache hit ratios and lower memory consumption.

### Observability & Structured Logging
- **Structured JSON Logs**: Unified log output formatting to structured JSON across the Java Backend and FastAPI AI microservice for seamless ingestion.
- **Request Correlation & Tracing**: Implemented W3C Trace Context (`traceparent`) propagation across HTTP boundary headers, ensuring end-to-end request tracing.
- **Prometheus Metrics & Grafana**: Exported key application performance metrics (`http_requests_total`, `http_request_duration_seconds`, `ai_inference_duration_seconds`) paired with pre-configured Grafana monitoring dashboards.

### Reliability & Health Endpoints
- **Actuator & Service Health Probes**: Configured dedicated readiness (`/actuator/health/readiness`, `/health/readiness`) and liveness (`/actuator/health/liveness`, `/health/liveness`) endpoints for Kubernetes and Docker container orchestrators.
- **Circuit Breaker Fault Tolerance**: Integrated resilience patterns for AI microservice calls, providing zero-downtime fallback to local rule-based recommendations during service degradation or network timeouts.
- **Disaster Recovery & Graceful Shutdown**: Standardized graceful shutdown hooks across all containers, ensuring inflight requests complete cleanly before process termination.

### Code Quality & Release Readiness
- **TypeScript & ESLint Standardization**: Resolved strict type warnings and linting checks across React components.
- **Unified Versioning**: Synchronized version `v1.0.1` across Java Backend (`pom.xml`), React Frontend (`package.json`), Python AI Service (`pyproject.toml`), Python SDK (`pyproject.toml`), and Docker build manifests.

---

## Testing

PricePilot v1.0.1 underwent extensive automated testing across all system components, achieving a **100% regression pass rate**:

- **Backend (Java Spring Boot)**: `74/74` unit and integration tests passing (`.\mvnw.cmd clean verify`).
- **Frontend (React TypeScript)**: `14/14` tests passing with 0 TypeScript/ESLint errors and 0 high-severity vulnerability findings (`npx vitest run`).
- **AI Microservice (FastAPI)**: `11/11` pytest tests passing (`uv run pytest`), validating startup lifespans, health checks, and inference endpoints.
- **Python SDK**: `41/41` pytest tests passing (`uv run pytest`) across authentication, search, recommendation, and dataset namespaces.

---

## Compatibility

PricePilot v1.0.1 fully supports the following runtime environments:

- **Java**: OpenJDK 21 (LTS)
- **Node.js**: Node 20+ (with Vite 8 & React 19)
- **Python**: Python 3.10+
- **Docker**: Docker Engine 24+ and Docker Compose v2+
- **PostgreSQL**: PostgreSQL 16
- **Redis**: Redis 7 (Alpine)

### API & Database Compatibility
- **Zero Breaking API Changes**: Fully backwards-compatible REST endpoints and SDK methods.
- **Automated Database Schema Migration**: No manual database migrations required. Schema updates are automatically managed via bundled Flyway migration scripts upon startup.

---

## Upgrade Notes

Upgrading to PricePilot v1.0.1 is straightforward and requires zero downtime for containerized deployments:

1. **Pull Latest Version**: Fetch the `v1.0.1` release tag or pull updated source code from the repository.
   ```bash
   git fetch --tags
   git checkout v1.0.1
   ```
2. **Update Environment Variables**: Review `.env.example` for optional new telemetry variables (e.g., Prometheus scrapers or log level overrides). Existing configurations remain fully valid.
3. **Deploy Containers**: Rebuild and start container services using Docker Compose:
   ```bash
   docker compose build --no-cache
   docker compose up -d
   ```
4. **Automated Flyway Execution**: The Java backend automatically applies any pending Flyway schema migrations on startup.
5. **Verify Health Endpoints**: Ensure all services are healthy by querying the readiness probes:
   - Backend: `GET /actuator/health/readiness`
   - AI Service: `GET /health/readiness`

---

## Known Limitations

PricePilot v1.0.1 addresses all critical stability and operational requirements. Identified areas for ongoing future refinement include:

- **Distributed Tracing Protocol Expansion**: W3C Trace Context propagation is active across microservices; native OpenTelemetry collector auto-instrumentation will be expanded in a future update.
- **Centralized Log Aggregation Pipeline**: Structured JSON logging is enabled on standard output streams; centralized log streaming drivers (such as Grafana Loki or ELK) can be configured externally.
- **Advanced Horizontal Auto-scaling**: Container health probes and stateless sessions are ready for scaling; auto-scaling policy templates for Kubernetes HPA will be provided in upcoming infrastructure releases.

---

## Acknowledgements

We extend our sincere gratitude to all open-source contributors, maintainers, and community members whose feedback and testing made this maintenance release possible. Your contributions continue to strengthen PricePilot's foundation.

---

## What's Next

With production readiness, system reliability, and observability established in v1.0.1, core development will pivot back toward expanding user-facing capabilities.

For details on upcoming features, multi-retailer engine expansion, and enhanced analytics capabilities, refer to the **PricePilot v1.1 Roadmap**.
