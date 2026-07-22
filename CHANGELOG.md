# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-07-22

### Added
- **M4 Observability**: Structured JSON logging, W3C Trace Context propagation, Prometheus custom metrics (`http_requests_total`, `http_request_duration_seconds`, `ai_inference_duration_seconds`), Grafana dashboard provisioning, and Actuator health readiness/liveness endpoints.
- **M5 System Reliability & Resilience**: Circuit breaker patterns for AI integration with seamless local rule-based fallback, rate limiting (Bucket4j/Redis), graceful shutdown hooks across containers, health-check probes, and multi-tier failure recovery.
- **M6 Maintenance & Stewardship**: Version alignment (v1.0.1) across all microservices, SDKs, and Docker images; cleaned unused imports and obsolete artifacts; standardized static analysis and code quality compliance; updated comprehensive release readiness audit report.

### Changed
- Unified application versioning across Java Backend (`pom.xml`), React Frontend (`package.json`), Python AI Microservice (`pyproject.toml`), and Python SDK (`pyproject.toml`).
- Enhanced code quality, type safety, and static analysis compliance across all subprojects.

### Fixed
- Fixed strict TypeScript and ESLint type warnings across React component layers.

---

## [1.0.0-rc1] - 2026-07-12

### Added
- **Java Backend**:
  - Implemented multi-faceted search and sorting (price ascending/descending, discount percentage descending).
  - Integrated JSON Web Token (JWT) stateless authentication filter with account status validation on each request.
  - Implemented saved products (favorites) and price watchlists features.
  - Added robust validation constraints on DTO schemas and established a global exception handler.
  - Integrated Redis caching layer for performance optimization and mitigation of polymorphic deserialization attacks.
  - Added Spring Boot Actuator metrics and Prometheus configuration for application observability.
- **AI Assistant Microservice**:
  - Developed a FastAPI assistant chatbot leveraging LLMs for comparisons, search filters, and recommendations.
  - Added prompt injection shielding using structural XML boundary tag isolation.
  - Developed API-key authentication and scoped conversation memory by user email prefix.
  - Implemented a model hot reload endpoint secured with authorization tokens.
- **Python SDK**:
  - Built a production-grade, type-safe Python SDK with modular namespaces (`client.auth`, `client.products`, `client.recommendations`, `client.watchlists`, `client.dashboard`, `client.analytics`, `client.ml`, `client.ai`).
  - Added automatic connection pooling, exponential backoff retries, and typed exception mapping.
  - Implemented credentials sanitization in error logs.
- **React Frontend**:
  - Built a responsive single-page application using React 19, Vite 8, Tailwind CSS v4, and Shadcn UI.
  - Implemented smooth page transitions and micro-interactions powered by Framer Motion.
  - Integrated timeline widgets for historical price visualizations.
- **CI/CD & DevOps**:
  - Docker Compose orchestration config supporting multi-tier dependency health checks.
  - Automated dependency audits and security scans (SonarQube, Trivy, Snyk).
  - Added a `.gitattributes` file to standardize line endings (LF) across operating systems.

### Changed
- Refactored `UserPrincipal` context loading to reconstruct user details directly from claims, eliminating a database query per authenticated request.
- Optimized query footprints for Watchlist creation from 5 database roundtrips down to 2 using custom repository fetch queries.
- Cleaned up duplicate documentation and untracked files in the `docs` folder.

### Security
- Whitelisted permitted serialization packages in the Redis cache manager.
- Hardened CORS configuration allowing specified domains only.
- Added security headers (`X-Frame-Options`, `X-Content-Type-Options`, `Content-Security-Policy`, `Permissions-Policy`, `Referrer-Policy`) to all API responses.
