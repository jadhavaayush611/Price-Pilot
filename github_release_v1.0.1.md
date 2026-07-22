# PricePilot v1.0.1

Release date: 2026-07-22

## Overview

PricePilot v1.0.1 is a production-readiness and maintenance release focused on security hardening, performance optimization, resilience, and comprehensive observability across all microservices. This release introduces no breaking API changes or new user-facing features, delivering a robust, enterprise-ready foundation.

## Key Highlights

- **Security Hardening**: Enforced explicit package allowlists for Redis cache deserialization and standardized HTTP security headers across backend API responses.
- **Performance & Caching**: Reduced database query roundtrips during watchlist operations, optimized stateless JWT principal loading, and streamlined Redis cache payload sizes.
- **Observability & Logging**: Implemented structured JSON logging, end-to-end W3C Trace Context propagation, Prometheus metrics (`http_requests_total`, `http_request_duration_seconds`, `ai_inference_duration_seconds`), and pre-configured Grafana dashboards.
- **Resilience & Reliability**: Added circuit breaker fault tolerance for AI microservice calls with automatic local rule-based fallback, distributed rate limiting, and Kubernetes/container readiness/liveness health probes.
- **Version Synchronization**: Unified application versioning (`v1.0.1`) across Java Backend, React Frontend, FastAPI AI Microservice, Python SDK, and Docker build manifests.

## Testing & Quality Assurance

PricePilot v1.0.1 achieves a **100% regression pass rate** across all automated test suites:

- **Backend (Java Spring Boot)**: 74/74 tests passing
- **Frontend (React / Vite)**: 14/14 tests passing (0 ESLint/TypeScript errors, 0 high security vulnerabilities)
- **AI Microservice (FastAPI)**: 11/11 tests passing
- **Python SDK**: 41/41 tests passing

## Compatibility

- **Runtimes**: Java 21, Node.js 20+, Python 3.10+
- **Infrastructure**: Docker & Docker Compose, PostgreSQL 16, Redis 7
- **Database Migrations**: Handled automatically via bundled Flyway migration scripts
- **API Status**: 100% backwards compatible (no breaking API changes)

## Getting Started & Upgrade

### Docker Compose Quickstart

To upgrade or deploy PricePilot v1.0.1 using Docker Compose:

```bash
# Clone or pull the v1.0.1 release tag
git fetch --tags
git checkout v1.0.1

# Build and start services
docker compose build --no-cache
docker compose up -d

# Verify system health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8000/health/readiness
```

### Python SDK Installation

```bash
pip install pricepilot-sdk==1.0.1
```

For full release details and complete documentation, refer to [RELEASE_NOTES_v1.0.1.md](RELEASE_NOTES_v1.0.1.md) and [CHANGELOG.md](CHANGELOG.md).
