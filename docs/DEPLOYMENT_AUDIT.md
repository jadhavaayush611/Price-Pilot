# PricePilot Comprehensive Cloud & Deployment Audit (v1.0 Pre-Release)

**Audit Date:** July 11, 2026  
**Auditor:** Independent Cloud Architecture Review Board  
* Principal Cloud Architect (AWS)
* Senior DevOps Engineer
* Kubernetes Platform Engineer
* Site Reliability Engineer (SRE)
* Docker & Container Specialist
* CI/CD Engineer
* Infrastructure Engineer
* Production Operations Lead

---

## Executive Summary

PricePilot is a modern, high-performance product price comparison search engine with a hybrid architecture consisting of a Spring Boot backend orchestrator, a React/Nginx frontend, and a FastAPI AI microservice. While the application logic, caching setup, and service boundaries are architecturally sound and modular, the project is **not deployment-ready** for a public v1.0 release. 

A fresh/clean deployment of the application currently crashes immediately on startup. Critical security vulnerabilities, such as hardcoded JWT secrets, exposed internal database ports, and missing authentication for Redis are present. Crucial features like the FastAPI AI service are completely omitted from the local orchestration orchestration (`docker-compose.yml`). 

With targeted, minor-to-moderate changes in database migrations, environment configurations, and orchestration setups, the system can be successfully hardened and prepared for startup-scale production hosting.

---

## Cloud Scores Matrix

| Review Section | Auditing Focus | Score |
| :--- | :--- | :---: |
| **Section 1** | Docker & Base Images | **6.5 / 10** |
| **Section 2** | Docker Compose Orchestration | **4.0 / 10** |
| **Section 3** | Spring Boot Deployment Configuration | **5.0 / 10** |
| **Section 4** | FastAPI Service Deployment | **6.5 / 10** |
| **Section 5** | Database Deployment & Schema Migrations | **3.0 / 10** |
| **Section 6** | Redis Caching & Cache Configuration | **4.5 / 10** |
| **Section 7** | Environment & Secrets Configuration | **4.0 / 10** |
| **Section 8** | Logging & Observability | **5.0 / 10** |
| **Section 9** | Monitoring & Health Checks | **4.0 / 10** |
| **Section 10** | CI/CD Readiness | **1.0 / 10** |
| **Section 11** | Infrastructure Architecture | **8.0 / 10** |
| **Section 12** | Disaster Recovery & Operations | **3.0 / 10** |
| **Section 13** | Developer Onboarding & Local Setup | **4.0 / 10** |
| **Overall** | **Weighted Deployment Readiness Score** | **4.9 / 10** |

---

## Final Recommendation

### 🔴 REQUIRES DEPLOYMENT REWORK

The codebase contains structural blockers that prevent a clean deployment from succeeding and pose severe security vulnerabilities in public hosting. The project must resolve the identified Release Blockers before the official v1.0 release.

---

## Section-by-Section Review

### Section 1 — Docker
*   **Evaluation:**
    *   **Backend (`backend/Dockerfile`):** Multi-stage build separates Maven build from JRE runtime. However, it uses `eclipse-temurin:25-jdk` and `eclipse-temurin:25-jre` which are non-standard/pre-release JDK versions (JDK 25 is not a stable LTS). Furthermore, the container runs as root.
    *   **Frontend (`frontend/Dockerfile`):** Multi-stage build using `node:20-alpine` and `nginx:alpine`. Configured correctly for single-page routing via `nginx.conf`, but embeds `VITE_API_BASE_URL` at build-time using `ARG` and `ENV`.
    *   **FastAPI AI (`pricepilot-ai/Dockerfile`):** Uses a single-stage `python:3.11-slim` base image. It copies the model pickles directly into the image.
*   **Vulnerabilities & Technical Risks:**
    *   **Root Privilege Escalation:** All containers run as `root`, increasing the blast radius of container escapes.
    *   **Hardcoded Build-Time Frontend URL:** React API calls are hardcoded at build time. Re-deploying the frontend to a different environment requires rebuilding the image rather than modifying environment variables at runtime.
    *   **Outdated/Future JRE:** Eclipse Temurin 25 is not a standard production JRE.
*   **Recommended Fixes:**
    *   Downgrade Temurin images to `eclipse-temurin:21-jre` (stable LTS).
    *   Add non-privileged users in the runtime stage of all Dockerfiles (e.g. `USER nobody` or custom `spring`/`nginx` non-root users).
    *   Expose port 8080 in backend and 80 in Nginx, but run Nginx on a non-privileged port (e.g., 8080) internally to avoid requiring root to bind to port 80.
*   **Score:** **6.5 / 10**

---

### Section 2 — Docker Compose
*   **Evaluation:**
    *   Defines `postgres`, `redis`, `backend`, and `frontend`.
    *   Uses named volumes `postgres_data` and `redis_data` for database persistence.
    *   Configures dependency startup order with `depends_on` and health checks.
*   **Vulnerabilities & Technical Risks:**
    *   **Missing Critical Service:** The `pricepilot-ai` FastAPI service is completely missing from `docker-compose.yml`. Without it, recommendations and the AI shopping assistant will fail.
    *   **Exposed Internal Infrastructure Ports:** Postgres (`5432`) and Redis (`6379`) are exposed directly to the public host. Any external actor can access these databases if the host firewall is not configured.
    *   **Broken Container Communication:** The backend depends on `pricepilot-ai`, but the AI URL environment variable is not passed in the backend service configuration, causing the backend to default to `http://localhost:8000`.
*   **Recommended Fixes:**
    *   Add the `pricepilot-ai` service to `docker-compose.yml` with health checks.
    *   Set `PRICEPILOT_AI_URL=http://pricepilot-ai:8000` in the backend service environment.
    *   Bind internal ports to localhost (e.g., `127.0.0.1:5432:5432`) to prevent exposing them to the internet.
*   **Score:** **4.0 / 10**

---

### Section 3 — Spring Boot Deployment
*   **Evaluation:**
    *   Uses profiles (`dev` and `prod`). Exposes micrometer metrics for Prometheus scraping.
*   **Vulnerabilities & Technical Risks:**
    *   **Production DDL Auto:** `spring.jpa.hibernate.ddl-auto` is set to `update` in `application-prod.properties`. Using `update` in production alongside Flyway can cause silent, automated schema updates, breaking schema locking and synchronization.
    *   **Hardcoded CORS Allowed Origins:** `SecurityConfig.java` has allowed origins hardcoded to `http://localhost:5173` and `http://localhost:3000`. In production, the Nginx frontend runs on port 80 (`http://localhost`), meaning all frontend requests will be blocked by CORS!
    *   **Unsecured Actuator Endpoints:** Actuator endpoints are mapped under `permitAll()`, exposing system stats and metrics to the public.
    *   **RCE Deserializer Vulnerability:** `CacheConfig.java` activates default typing (`NON_FINAL`) on the Redis JSON serializer, exposing the JVM to Remote Code Execution (RCE) via untrusted deserialization.
    *   **No Graceful Shutdown:** Graceful shutdown is not enabled, leading to dropped active connections during scaling/deployments.
*   **Recommended Fixes:**
    *   Set `spring.jpa.hibernate.ddl-auto=validate` or `none` in `application-prod.properties`.
    *   Inject CORS allowed origins via environment variables (e.g. `PRICEPILOT_CORS_ALLOWED_ORIGINS`).
    *   Secure Actuator endpoints so only authenticated admins can scrape metrics, or restrict `/actuator/**` access to local networking.
    *   Enable graceful shutdown: `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.
    *   Replace `RestTemplate`'s default `SimpleClientHttpRequestFactory` with an Apache HttpClient/OkHttp factory that supports HTTP connection pooling.
*   **Score:** **5.0 / 10**

---

### Section 4 — FastAPI Deployment
*   **Evaluation:**
    *   Includes API key authorization (`X-API-Key`) and basic Prometheus metric routes. Supports atomic model reload.
*   **Vulnerabilities & Technical Risks:**
    *   **Single-Threaded Worker Execution:** Runs Uvicorn directly with default settings. CPU-bound model predictions will block the single event loop, causing severe latency degradation under concurrency.
    *   **Undocumented Gemini Keys:** The AI assistant calls the Gemini API directly, but the required keys are not part of settings or `.env`.
*   **Recommended Fixes:**
    *   Deploy with Gunicorn using Uvicorn worker classes (`gunicorn -w 4 -k uvicorn.workers.UvicornWorker app.main:app`) to leverage multiple CPU cores.
    *   Consolidate all environment variables, including `GEMINI_API_KEY`, inside the unified Pydantic/Settings class.
*   **Score:** **6.5 / 10**

---

### Section 5 — Database Deployment
*   **Evaluation:**
    *   PostgreSQL is deployed via standard alpine images. Flyway migrations exist from V1.1 to V1.7.
*   **Vulnerabilities & Technical Risks:**
    *   **Flyway Initialization Gap (Critical Crash):** There is no `V1__init.sql` schema file creating the primary tables (`products`, `sellers`, `product_prices`). They are expected to be created by Hibernate's `ddl-auto=update`. However, Flyway runs *before* Hibernate. Consequently, on a fresh database, Flyway attempts to run `V1.1__add_performance_indexes.sql` on non-existent tables, causing an immediate SQL exception and crash.
*   **Recommended Fixes:**
    *   Add a baseline `V1.0__init.sql` migration that explicitly creates the core tables (`products`, `sellers`, `product_prices`) so the schema is self-contained and fully reproducible.
*   **Score:** **3.0 / 10**

---

### Section 6 — Redis Deployment
*   **Evaluation:**
    *   Uses standard Redis 7 image. Implements custom Spring Boot cache serializer and configurations.
*   **Vulnerabilities & Technical Risks:**
    *   **No Authentication:** Redis is running without authentication. Since the port is exposed publicly in Docker Compose, it is fully vulnerable.
    *   **OOM Risk:** No maximum memory or eviction policies are configured, making the server vulnerable to OOM crashes under caching workloads.
    *   **No AOF Persistence:** Persistence is left to default RDB snapshotting. If the Redis container restarts, cached data since the last snapshot is lost.
*   **Recommended Fixes:**
    *   Run Redis with `--requirepass ${REDIS_PASSWORD}` in Docker Compose and update `CacheConfig` accordingly.
    *   Set eviction policy: `maxmemory 256mb` and `maxmemory-policy allkeys-lru`.
*   **Score:** **4.5 / 10**

---

### Section 7 — Environment Configuration
*   **Evaluation:**
    *   Exposes DB ports, username, password, active profiles, and frontend API URLs in `.env`.
*   **Vulnerabilities & Technical Risks:**
    *   **Hardcoded JWT Secret:** The JWT signing key is hardcoded in Java code (`JwtService.java`) with no environment mapping in production configurations.
    *   **Weak Defaults:** Database passwords and API keys default to `postgres` and `pricepilot-secret-api-key`.
*   **Recommended Fixes:**
    *   Add `PRICEPILOT_JWT_SECRET` and `GEMINI_API_KEY` to the `.env` file, and reference them in `application-prod.properties` and the FastAPI configuration.
*   **Score:** **4.0 / 10**

---

### Section 8 — Logging & Observability
*   **Evaluation:**
    *   FastAPI implements structured JSON logging. Backend Spring Boot uses standard stdout text logs.
*   **Vulnerabilities & Technical Risks:**
    *   **Unstructured Backend Logs:** Plain-text logs are difficult to parse in log aggregators (Elasticsearch, CloudWatch).
    *   **No Distributed Tracing:** The backend does not propagate correlation IDs (`X-Request-ID`) to the FastAPI service, preventing cross-container request tracing.
    *   **Incomplete Cache Metrics:** The custom `InstrumentedCache` does not count cache hits during synchronous lookups, under-reporting performance.
*   **Recommended Fixes:**
    *   Add `logback-spring.xml` configuring `LogstashEncoder` to emit structured JSON logs in the production profile.
    *   Implement a Spring Web client filter that extracts the incoming request ID and forwards it as `X-Request-ID` in outgoing HTTP requests to the FastAPI service.
*   **Score:** **5.0 / 10**

---

### Section 9 — Monitoring & Health Checks
*   **Evaluation:**
    *   Backend metrics are exposed via Actuator's Prometheus endpoint. FastAPI exposes metrics at `/metrics`.
*   **Vulnerabilities & Technical Risks:**
    *   **Dummy Health Check Endpoint:** The backend's health check is mapped to a custom controller at `/api/v1/health` that always returns `{"status": "UP"}` statically. If the database or Redis is completely down, this check still returns `UP`, causing container orchestrators to report broken instances as healthy.
*   **Recommended Fixes:**
    *   Point the container health checks in `Dockerfile` and `docker-compose.yml` to the Actuator health endpoint `/actuator/health` which performs real connectivity checks.
*   **Score:** **4.0 / 10**

---

### Section 10 — CI/CD Readiness
*   **Evaluation:**
    *   No CI/CD pipelines, configurations, or workflow files exist.
*   **Vulnerabilities & Technical Risks:**
    *   **No Automation:** Testing, linting, formatting, security scanning, and container packaging must be executed manually.
*   **Recommended Fixes:**
    *   Establish a `.github/workflows/` directory and configure GitHub Actions workflows for continuous integration, linting, and automated Docker image building. Projections for these workflows are provided below.
*   **Score:** **1.0 / 10**

---

### Section 11 — Infrastructure Architecture
*   **Evaluation:**
    *   Responsibilities are well separated: Nginx serves static assets; Spring Boot handles transactions, auth, and database persistence; FastAPI runs inference.
*   **Vulnerabilities & Technical Risks:**
    *   **Blocked Backend Threads:** Backend communication with FastAPI is synchronous. If the AI service experiences latency spikes, Spring Boot's request threads will block, exhausting the Tomcat thread pool.
    *   **Monolithic Model Loading:** ML models are bundled inside the FastAPI Docker image rather than fetched dynamically from an object registry.
*   **Recommended Fixes:**
    *   Integrate a circuit breaker (e.g. Resilience4j) to protect the backend threads from AI service latency spikes.
    *   Implement dynamic model fetching from S3 or an MLflow model registry on startup rather than bundling pickles in the Docker image.
*   **Score:** **8.0 / 10**

---

### Section 12 — Disaster Recovery & Operations
*   **Evaluation:**
    *   Data persistence is handled via named volumes.
*   **Vulnerabilities & Technical Risks:**
    *   **No Backup Policies:** There are no backup scripts, restore tests, or disaster recovery documentation.
    *   **Lack of Auto-Recovery:** No restart policies are configured in `docker-compose.yml`. If a container crashes due to transient errors, it remains stopped.
*   **Recommended Fixes:**
    *   Set `restart: unless-stopped` on all services in `docker-compose.yml`.
    *   Add a script to export postgres backups (`pg_dump`) to an external target.
*   **Score:** **3.0 / 10**

---

### Section 13 — Developer Onboarding & Local Setup
*   **Evaluation:**
    *   Onboarding guides are clean, but local scripts do not account for starting the FastAPI AI service.
*   **Vulnerabilities & Technical Risks:**
    *   **Clean Startup Failures:** A developer running `docker compose up` for the first time will encounter a database initialization crash and a missing AI service.
*   **Recommended Fixes:**
    *   Resolve Flyway migrations and add the AI service to the docker-compose orchestration.
    *   Update the root `package.json` to launch the FastAPI dev server alongside the backend and frontend.
*   **Score:** **4.0 / 10**

---

### Section 14 — Cloud Readiness
*   **Deployment Options:**
    *   **Startup-Scale (Railway, Render, AWS ECS on Fargate):** Excellent targets. They support multi-service container hosting, managed databases (RDS, Cloud SQL), and key management.
    *   **Enterprise-Scale (AWS EKS, GCP GKE, Azure AKS):** Overkill for the current requirements, but viable for high horizontal scale.
*   **Required Changes for Cloud Deployment:**
    *   Decouple frontend build arguments: Nginx should reverse-proxy API calls (e.g., proxy `/api` requests to backend URL), allowing the frontend image to remain environment-agnostic.
    *   Deploy PostgreSQL and Redis using managed cloud services (e.g., AWS RDS PostgreSQL, AWS ElastiCache Redis) rather than running them in self-hosted containers to ensure high availability, automatic backups, and vertical scaling.
    *   Integrate cloud secret managers (e.g., AWS Secrets Manager) to inject database passwords, JWT secrets, and Gemini keys.

---

### Section 15 — Future Infrastructure Roadmap
The following improvements are recommended for subsequent phases:
1.  **Infrastructure as Code (IaC):** Use Terraform to provision AWS resources (VPC, RDS, ECS, ElastiCache, S3).
2.  **Observability Suite:** Standardize logging with OpenTelemetry and export metrics to Prometheus/Grafana and traces to Jaeger.
3.  **CI/CD Pipeline Security:** Add automated security tooling in GitHub Actions:
    *   **Dependency Scanning:** Use Snyk or GitHub Dependabot.
    *   **Vulnerability Scanning:** Use Trivy to inspect Docker images for base OS vulnerabilities.
    *   **Secret Detection:** Use GitGuardian to prevent secrets from being committed.

---

## Action Plan & Concrete Fixes

### 1. Fix Database Initial Migration (Flyway)
Create a new file `V1.0__init.sql` under `backend/src/main/resources/db/migration/` containing the table structures, ensuring Flyway can run successfully on a clean database before the indexes are added:

```sql
-- V1.0__init.sql
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    category VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(255),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sellers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS product_prices (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
    price NUMERIC(10, 2) NOT NULL,
    original_price NUMERIC(10, 2),
    discount_percentage NUMERIC(5, 2),
    in_stock BOOLEAN NOT NULL DEFAULT TRUE,
    url VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Rename the original index migration from `V1.1__add_performance_indexes.sql` to `V1.1__indexes.sql` or leave it, ensuring it runs on top of `V1.0`.

---

### 2. Proposing a Secure, Multi-Service `docker-compose.yml`
Here is an updated `docker-compose.yml` that registers the AI service, restricts port access, and configures authentication and health checks:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: pricepilot-db
    # Bind port to localhost only for developer tool connections; prevent external exposure
    ports:
      - "127.0.0.1:${DB_PORT:-5432}:5432"
    environment:
      POSTGRES_DB: ${DB_NAME:-pricepilot}
      POSTGRES_USER: ${DB_USER:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    networks:
      - pricepilot-network

  redis:
    image: redis:7-alpine
    container_name: pricepilot-redis
    # Run redis with security password enabled
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "127.0.0.1:6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    networks:
      - pricepilot-network

  pricepilot-ai:
    build:
      context: .
      dockerfile: pricepilot-ai/Dockerfile
    container_name: pricepilot-ai
    ports:
      - "127.0.0.1:8000:8000"
    environment:
      PRICEPILOT_AI_API_KEY: ${PRICEPILOT_AI_API_KEY}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      ENV: production
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 15s
      timeout: 5s
      retries: 3
    restart: unless-stopped
    networks:
      - pricepilot-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: pricepilot-backend
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${DB_NAME:-pricepilot}
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-postgres}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      PRICEPILOT_AI_URL: http://pricepilot-ai:8000
      PRICEPILOT_AI_API_KEY: ${PRICEPILOT_AI_API_KEY}
      PRICEPILOT_JWT_SECRET: ${PRICEPILOT_JWT_SECRET}
      PRICEPILOT_CORS_ALLOWED_ORIGINS: ${PRICEPILOT_CORS_ALLOWED_ORIGINS:-http://localhost}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      pricepilot-ai:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      start_period: 30s
      retries: 3
    restart: unless-stopped
    networks:
      - pricepilot-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        - VITE_API_BASE_URL=${VITE_API_BASE_URL:-http://localhost:8080/api/v1}
    container_name: pricepilot-frontend
    ports:
      - "${FRONTEND_PORT:-80}:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - pricepilot-network

volumes:
  postgres_data:
  redis_data:

networks:
  pricepilot-network:
    driver: bridge
```

---

### 3. Proposing CI/CD Workflows (GitHub Actions)
Add a workflow file `.github/workflows/ci.yml` to automatically run tests and builds:

```yaml
name: PricePilot Continuous Integration

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Build & Test Backend
        run: |
          cd backend
          chmod +x mvnw
          ./mvnw clean test

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      - name: Install & Test Frontend
        run: |
          cd frontend
          npm ci
          npm run build

  ai-service-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'
          cache: 'pip'
      - name: Install & Test AI Service
        run: |
          cd pricepilot-ai
          pip install -r requirements.txt
          pip install pytest
          PYTHONPATH=. pytest
```

---

## Findings Categorization

### 🔴 Release Blockers
*   **Flyway Database Initialization Crash:** On a fresh database, Flyway crashes due to references to tables (`product_prices`, `sellers`) that do not exist yet. Fix: Create `V1.0__init.sql` to instantiate the schema prior to indexes.
*   **CORS Production Port Blocking:** Allowed origins in `SecurityConfig.java` are hardcoded to dev ports (`5173`, `3000`). This blocks frontend requests in production. Fix: Externalize CORS origins through an environment variable.
*   **Hardcoded JWT Signing Key:** Production uses a default hardcoded JWT key fallback in `JwtService.java`. Fix: Add a required `PRICEPILOT_JWT_SECRET` environment variable check.
*   **Missing AI Microservice in Compose:** The `pricepilot-ai` container is missing from `docker-compose.yml`. Fix: Add the FastAPI container to the compose orchestration.
*   **Incorrect Service Networking Host:** Backend defaults to `http://localhost:8000` to reach the AI service, which fails inside container networks. Fix: Configure environment mappings in docker-compose.

### 🟡 High Priority
*   **Dummy Health Check:** `/api/v1/health` is hardcoded to return `UP`, failing to detect database or cache failures. Fix: Update Docker health checks to target `/actuator/health`.
*   **Redis Security Vulnerability:** Redis runs without password authentication and exposes its port (`6379`) to the public host. Fix: Configure `--requirepass` and bind port 6379 to `127.0.0.1`.
*   **PostgreSQL Exposed Port:** Database port `5432` is exposed publicly. Fix: Bind the port to localhost only.
*   **Unsecured Actuator Endpoints:** Spring Boot Actuator endpoints are exposed under `permitAll()`, leaking internal operational metrics. Fix: Add Basic Authentication or secure Actuator routing.
*   **CPU Core Inference Blockage:** FastAPI runs on Uvicorn directly with one worker thread, risking prediction latencies. Fix: Use Gunicorn with Uvicorn worker classes.
*   **Root User Container Execution:** Containers run as `root` in all Dockerfiles. Fix: Add custom non-root users in Docker runtime stages.

### 🔵 Technical Debt
*   **Insecure Cache Deserializer:** Cache JSON serialization utilizes `DefaultTyping.NON_FINAL` on ObjectMapping, presenting deserialization RCE risks.
*   **Non-LTS JDK Image Choice:** Uses Java 25. Recommended: Standardize on Java 21 LTS.
*   **No Outbound HTTP Connection Pooling:** Outbound connections use raw `RestTemplate` with `SimpleClientHttpRequestFactory`.
*   **No Graceful Shutdown:** Shutdown endpoints terminate active client requests immediately.
*   **Unstructured Log Streaming:** Backend logs are text-only, preventing automated parsing.
*   **Incomplete Cache Metrics:** Synchronous cache lookups are not captured by custom hit metrics.
*   **Deprecated Starlette Handlers:** FastAPI uses deprecated `@app.on_event` routers.

### ⚪ Future Roadmap
*   **Infrastructure as Code (IaC):** Adopt HashiCorp Terraform for automated cloud provisioning.
*   **Distributed Logging & Monitoring:** Adopt OpenTelemetry + Prometheus + Grafana and Jaeger tracing.
*   **Secret Management Integration:** Integrate HashiCorp Vault or AWS Secrets Manager.
*   **Docker Security Pipelines:** Implement Trivy container scans and Snyk code vulnerability scans in CI.
*   **Elastic Outbound APIs:** Shift static model files from images to an object store (e.g. S3).
