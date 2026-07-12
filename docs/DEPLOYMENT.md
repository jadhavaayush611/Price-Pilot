# PricePilot Deployment Guide

This guide details how to build, run, and secure the PricePilot platform in containerized environments using Docker and Docker Compose.

---

## 1. Quick Start (Local Docker Compose)

The easiest way to start the entire system (Database, Redis, AI Service, Backend, and Frontend) is using Docker Compose.

### Prerequisites
* [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Docker Compose) installed and running.

### Launching the Application
1. Navigate to the project root directory.
2. Build and start all services in the background:
   ```bash
   docker compose up --build -d
   ```
3. Docker Compose will automatically build the images, create isolated networks, orchestrate the startup order using health checks, and launch the following services:
   * **PostgreSQL Database** (`pricepilot-db`): Runs on internal network port `5432` (mapped to host `127.0.0.1:5432` for dev access).
   * **Redis Cache** (`pricepilot-redis`): Runs on internal network port `6379` (mapped to host `127.0.0.1:6379` for dev access, authenticated with password).
   * **FastAPI AI Service** (`pricepilot-ai`): Runs on internal network port `8000` (mapped to host `127.0.0.1:8000` for dev access).
   * **Spring Boot REST API Backend** (`pricepilot-backend`): Runs on port `8080` (mapped to host `8080`).
   * **React Vite + Nginx Frontend** (`pricepilot-frontend`): Runs on port `8080` inside container (mapped to host port `80`).

4. Open your browser and navigate to:
   * **Frontend Application:** `http://localhost/`
   * **Backend Actuator Health Check:** `http://localhost:8080/actuator/health`
   * **AI Health Check:** `http://localhost:8000/health` (only accessible from localhost/127.0.0.1)

### Shutting Down
To stop and remove all containers, networks, and volumes:
```bash
docker compose down -v --remove-orphans
```

---

## 2. Container & Network Security Architecture

PricePilot runs as a microservices architecture inside a dedicated internal bridge network (`pricepilot-network`). 

For security hardening:
* **Zero Public Exposure for Internal Services:** PostgreSQL, Redis, and the AI Service do *not* expose ports to the public interface in the base configuration. Their ports are only accessible internally via the Docker bridge network.
* **Local Developer Access:** In local development, the `docker-compose.override.yml` maps these ports *only* to the loopback interface (`127.0.0.1`) of the host machine. They cannot be reached from the public network.
* **Exposed Gateways:** Only the frontend (`pricepilot-frontend` on port `80`) and the backend REST API (`pricepilot-backend` on port `8080`) are accessible publicly.

```
                              [ User Web Browser ]
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼ (Port 80)                                   ▼ (Port 8080)
      ┌──────────────────┐                           ┌──────────────────┐
      │    frontend      │                           │     backend      │
      │ (pricepilot-     │ ────────────────────────> │ (pricepilot-     │
      │   frontend)      │ (VITE_API_BASE_URL)       │   backend)       │
      └──────────────────┘                           └──────────────────┘
        (runs as nginx)                                (runs as appuser)
                                                        │   │          ▲
                   ┌────────────────────────────────────┘   │          │ (Port 8000)
                   │ (Port 5432)        ┌───────────────────┘          │
                   ▼                    ▼                              ▼
         ┌──────────────────┐ ┌──────────────────┐           ┌──────────────────┐
         │     database     │ │      redis       │           │    FastAPI AI    │
         │ (pricepilot-db)  │ │ (pricepilot-     │           │ (pricepilot-ai)  │
         └──────────────────┘ │   redis)         │           └──────────────────┘
                              └──────────────────┘             (runs as appuser)
                                (authenticated)
```

### Port Mappings
| Service Name | Container Name | Internal Port | Host Port (Production) | Host Port (Development) | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `pricepilot-db` | `pricepilot-db` | `5432` | *None* | `127.0.0.1:5432` | PostgreSQL Database Server |
| `pricepilot-redis` | `pricepilot-redis` | `6379` | *None* | `127.0.0.1:6379` | Redis Cache Server (Authenticated) |
| `pricepilot-ai` | `pricepilot-ai` | `8000` | *None* | `127.0.0.1:8000` | FastAPI Recommendation Engine |
| `pricepilot-backend`| `pricepilot-backend`| `8080` | `8080` | `8080` | Spring Boot REST API Backend |
| `pricepilot-frontend`| `pricepilot-frontend`| `8080` | `80` | `80` | Nginx Static React Assets |

---

## 3. Environment Variables & Secret Hardening

A `.env` file manages variables. Required production secrets have **no default fallbacks** in the production configuration. The applications will **fail fast** during startup if these variables are not provided:

| Variable | Description | Default / Local Dev Value | Production Requirement |
| :--- | :--- | :--- | :--- |
| `DB_NAME` | PostgreSQL database name | `pricepilot` | Custom database name |
| `DB_USER` | Database username | `postgres` | Non-superuser account |
| `DB_PASSWORD` | Database password | `postgres` | **Mandatory** (startup fails if missing) |
| `REDIS_PASSWORD` | Redis authentication password | `pricepilot-secure-redis-password` | **Mandatory** (startup fails if missing) |
| `REDIS_MAX_MEMORY` | Redis maximum memory limit | `256mb` | Configurable memory limit |
| `REDIS_MAX_MEMORY_POLICY` | Redis memory eviction policy | `allkeys-lru` | Configurable eviction policy |
| `PRICEPILOT_CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed CORS origins | `http://localhost:5173,http://localhost:3000` | Frontend production domain(s) |
| `PRICEPILOT_AI_API_KEY` | API Security Key for AI auth | `pricepilot-secret-api-key` | **Mandatory** (startup fails if default or missing) |
| `PRICEPILOT_JWT_SECRET` | Base64-encoded JWT Token key | `MzJieXRlc3NlY...` | **Mandatory** (startup fails if default or missing) |
| `SPRING_PROFILES_ACTIVE`| Active Spring profile | `dev` | `prod` (forces fail-fast and JSON logs) |
| `PRICEPILOT_AI_URL` | AI Service API endpoint URL | `http://pricepilot-ai:8000` | Internally routed URL |
| `VITE_API_BASE_URL` | React client API URL | `http://localhost:8080/api/v1` | Public API URL |

---

## 4. Runtime Security & Non-Root Users

Every container is configured to run under a dedicated, low-privileged non-root user. No container executes processes as `root`.

### Runtime Configuration
1. **Spring Boot Backend Container:**
   * Runs as user `appuser` (UID `10001`, GID `10001`).
   * Uses the `eclipse-temurin:21-jre` base image (Java 21 LTS runtime).
   * The jar file is copied with correct ownership (`--chown=appuser:appgroup`).
   * Drop permissions and privilege escalation block (`no-new-privileges:true`) configured in compose.
2. **FastAPI AI Container:**
   * Runs as user `appuser` (UID `10001`, GID `10001`).
   * Application files and ML models are owned by `appuser`.
   * Unnecessary build utilities (`build-essential`) are stripped from the runtime image to reduce attack surface and size.
3. **React/Nginx Frontend Container:**
   * Nginx runs on port `8080` (since non-root users cannot bind to port `80`).
   * Runs as the default non-root user `nginx` (UID `101`).
   * Correct write/read permissions applied to Nginx configuration and cache folders (`/var/run/nginx.pid`, `/var/cache/nginx`, etc.).

---

## 5. Startup Ordering & Container Health Checks

The orchestration utilizes Docker healthchecks to enforce dependency order, manage liveness vs. readiness, and ensure that services do not receive traffic until they are fully ready:

### Health Checks and Readiness Probes

1. **Database Readiness (`pricepilot-db`):**
   * Command: `pg_isready -U ${DB_USER} -d ${DB_NAME}`
   * Role: Ensures the database port is active and accepting connections before dependent services boot.

2. **Redis Readiness (`pricepilot-redis`):**
   * Command: `redis-cli -a ${REDIS_PASSWORD} ping`
   * Role: Verifies Redis cache readiness and authorization.

3. **AI Service Probes (`pricepilot-ai`):**
   * **Liveness Endpoint:** `GET http://localhost:8000/health/liveness` (returns `{"status": "UP"}` if uvicorn is responding).
   * **Readiness Endpoint:** `GET http://localhost:8000/health/readiness` (returns `{"status": "UP"}` and details if machine learning models are loaded. Returns `503 Service Unavailable` if not ready).
   * Docker Health Check: Configured to probe `/health/readiness` to block downstream services until models are in memory.

4. **Backend Probes (`pricepilot-backend`):**
   * **Liveness Endpoint:** `GET http://localhost:8080/actuator/health/liveness` (checks JVM/application state).
   * **Readiness Endpoint:** `GET http://localhost:8080/actuator/health/readiness` (verifies DB, Redis, and AI service connectivity).
   * Docker Health Check: Probes `/actuator/health/readiness` to ensure the API is fully operational before exposing it to the frontend.

5. **Frontend Probes (`pricepilot-frontend`):**
   * Command: `wget --spider -q http://localhost:8080/ || exit 1`
   * Role: Confirms Nginx is healthy and able to serve the built static resources.

---

## 6. Startup & Shutdown Sequences

### Startup Sequence
1. **Infrastructure Provisioning:** `pricepilot-db` and `pricepilot-redis` start.
2. **AI Engine Warmup:** `pricepilot-ai` boots, verifies configuration, and loads pickle model weights into memory.
3. **Database Migration:** Once the database is online, `pricepilot-backend` starts, and Flyway automatically runs database migrations.
4. **Backend Warmup:** The Spring Boot backend runs startup diagnostics, logging details of active profiles, versions, and connectivity.
5. **Gateway Launch:** Once the backend reports healthy, `pricepilot-frontend` boots up Nginx to serve incoming client requests.

### Graceful Shutdown Sequence
PricePilot containers are configured for high-availability graceful shutdown to drain active requests without dropping user traffic:

1. **Frontend (Nginx):**
   * Configured with `STOPSIGNAL SIGQUIT` in the Dockerfile.
   * On shutdown, Nginx finishes serving active HTTP connections before terminating.
   * `stop_grace_period: 15s` allows Nginx to fully drain.

2. **Spring Boot Backend:**
   * Configured with `server.shutdown=graceful` and a timeout of 30 seconds.
   * On receiving `SIGTERM`, Tomcat stops accepting new requests and allows active threads to complete.
   * `stop_grace_period: 45s` allows the backend to drain active database operations safely.

3. **FastAPI AI Service:**
   * Runs Uvicorn with `--timeout-graceful-shutdown 30`.
   * On receiving `SIGTERM`, Uvicorn drains active recommendation/chat request tasks.
   * `stop_grace_period: 30s` is configured.

4. **Redis and PostgreSQL:**
   * Configured with `stop_grace_period: 15s` to flush buffers and save states cleanly.

---

## 7. Logging & Observability

### Log Consistency
* **Production Logs:** When `SPRING_PROFILES_ACTIVE=prod`, Spring Boot backend outputs logs in structured JSON format (`ecs`). In `pricepilot-ai`, logs are printed as raw JSON to stdout.
* **Development Logs:** In dev profiles, logs are formatted as pretty human-readable console outputs.
* **ISO 8601 UTC Timestamps:** Standardized across all containers.
* **Request Correlation IDs:** HTTP requests are tagged with `X-Request-ID` correlation headers, which are propagated through HTTP calls for transaction tracing.

### Metrics Endpoints (Not Publicly Exposed)
* **Backend Actuator Metrics:** `/actuator/prometheus` and `/actuator/metrics` require authentication (role `ADMIN`) in production, preventing public scraping of internal performance metrics.
* **AI Service Metrics:** `/metrics` is secured behind `X-API-Key` header authentication to protect algorithm latency data.

---

## 8. Failure Recovery & Graceful Degradation

PricePilot is designed to degrade gracefully during operational anomalies:

| Failure Scenario | Impact | Application Response | Recovery Procedure |
| :--- | :--- | :--- | :--- |
| **PostgreSQL Unavailable** | Critical | Startup fails if DB is down. If DB goes down at runtime, requests requiring DB transactions return HTTP 500. | 1. Check DB logs: `docker compose logs pricepilot-db`. <br> 2. Verify volumes space. <br> 3. Restart: `docker compose restart pricepilot-db`. |
| **Redis Cache Unavailable** | Low | Application continues functioning. Caching operations degrade gracefully to direct database queries. | 1. Check Redis logs: `docker compose logs pricepilot-redis`. <br> 2. Cache manager logs warnings for failovers. <br> 3. Restart Redis: `docker compose restart pricepilot-redis`. |
| **AI Service Unreachable** | Medium | Personal recommendations fall back to database-driven popularity logic. Assistant requests return HTTP 503. | 1. Check AI service logs: `docker compose logs pricepilot-ai`. <br> 2. Ensure model pickle files are present under `./models`. <br> 3. Restart: `docker compose restart pricepilot-ai`. |

---

## 9. Troubleshooting Guide

### 1. Spring Boot Backend Fails to Start (Missing Secrets)
**Symptom:**
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'jwtService': Injection of autowired dependencies failed...
```
**Cause:**
`SPRING_PROFILES_ACTIVE=prod` is active, but required secrets like `PRICEPILOT_JWT_SECRET` or `SPRING_DATASOURCE_PASSWORD` are missing from the environment.
**Solution:**
Ensure you have copied and populated all keys in your `.env` file, and that they match the expected environment variable keys.

### 2. Redis Connection Refused / Auth Failures
**Symptom:**
```
RedisConnectionFailureException: Unable to connect to Redis
```
**Cause:**
Redis password mismatch or missing `REDIS_PASSWORD` in the `.env` file.
**Solution:**
Check that `REDIS_PASSWORD` is defined in `.env` and that `SPRING_DATA_REDIS_PASSWORD` is passed to the backend container.

### 3. AI Service Health Check Failing
**Symptom:**
`pricepilot-ai` container remains in starting state or becomes unhealthy.
**Cause:**
FastAPI failed to start or failed to load the model binary (`pkl`) files.
**Solution:**
Check the logs of the AI container:
```bash
docker compose logs pricepilot-ai
```
If logs show missing model files, ensure the `./models/recommendation` directory exists and has model pickle files (`popularity_model.pkl`, `content_model.pkl`, `collaborative_model.pkl`, `hybrid_model.pkl`). The local `models` directory is mounted into the container as a read-only volume, so it must contain valid trained models.

---

## 10. Post-Deployment Verification (Smoke Test)

A zero-dependency smoke test script is provided in the root directory (`smoke_test.py`). Run the following command after any deployment:

```bash
python smoke_test.py
```

This verifies:
1. Frontend reachable
2. Backend custom health check (`/api/v1/health` status UP)
3. Backend Actuator readiness check (`/actuator/health/readiness` status UP)
4. AI service readiness check (`/health/readiness` status UP)
5. User authentication registration & login flow
6. Personalized recommendations retrieval
7. AI Assistant interaction

---

## 11. Database Migrations (Flyway)

The application database schema is fully managed using **Flyway Database Migrations**.

* **Automatic Schema Migration:** When the `backend` container boots up, Flyway scans for pending SQL scripts in `src/main/resources/db/migration` and applies them automatically.
* **Baseline and Versioning:** Clean PostgreSQL databases initialize by executing the schema baseline script `V1.0__init.sql` followed by all subsequent scripts in order.
* **Strict Schema Validation:** Hibernate is configured with `ddl-auto=validate` in production, meaning it validates the schema at startup but makes no structural modifications itself. Flyway is the sole owner of database schema changes.

| Variable                | Required | Development | Production | Description                 |
| ----------------------- | -------- | ----------- | ---------- | --------------------------- |
| `SPRING_DATASOURCE_URL` | Yes      | ✓           | ✓          | PostgreSQL connection       |
| `PRICEPILOT_JWT_SECRET` | Yes      | ✓           | ✓          | JWT signing key             |
| `PRICEPILOT_AI_API_KEY` | Yes      | ✓           | ✓          | Backend ↔ AI authentication |
| `REDIS_PASSWORD`        | Yes      | ✓           | ✓          | Redis authentication        |
| `REDIS_MAX_MEMORY`       | No       | ✓ (256mb)   | ✓          | Redis memory limit          |
| `REDIS_MAX_MEMORY_POLICY`| No       | ✓ (allkeys-lru)| ✓        | Redis eviction policy       |
| `PRICEPILOT_CORS_ALLOWED_ORIGINS`| No | ✓ (default) | ✓          | Allowed origins list        |
