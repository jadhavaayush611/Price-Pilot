# PricePilot Security Policy

This document details the security model, configuration guidelines, hardening measures, and disclosure policy for the PricePilot application.

---

## 1. Authentication Model

PricePilot uses a stateless JSON Web Token (JWT) authentication pipeline for REST APIs.

- **Token Issue**: Authenticated logins via `/api/v1/auth/login` issue a signed HS256 JWT containing the user's role and user ID.
- **Verification**: On every incoming request, the `JwtAuthenticationFilter` validates the JWT signature and extracts the subject (email).
- **Database Validation**:
  - To prevent tokens from revoked, disabled, or locked accounts from being accepted, the filter validates the user against the database *on every request*.
  - Tokens belonging to deleted users, disabled users (`enabled = false`), or locked users (`locked = true`) are immediately rejected.
- **Secret Key Verification**: During application startup, `JwtService` runs a `@PostConstruct` validation. If the JWT secret key is missing, weak (less than 256 bits), or not valid Base64, the application fails to start.
- **Failure Logging**: Authentication failures are logged to the audit log safely without exposing the raw JWT, password, or any PII.

---

## 2. Authorization Model & IDOR Mitigation

Access control is enforced at both the API gateway and method levels.

### Spring Boot Controller Protections
- **Role-Based Access**: Role checks are performed via Spring Security `SecurityConfig`.
  - Admin endpoints (`/api/v1/sellers/**`, `/api/v1/prices/**`, `/api/v1/datasets/**`, `/actuator/**`) are restricted to `ROLE_ADMIN`.
  - User event listings `/api/v1/events/**` (except `/api/v1/events/me`) are restricted to `ROLE_ADMIN` to prevent cross-user leakage.
- **Ownership Validation (BOLA/IDOR)**:
  - Saved products and price watchlists are isolated at the database query level by resolving user identity from the authenticated SecurityContext (email/userID) rather than accepting client-provided user IDs.
  - Modifying or retrieving individual watchlists validates that the record's user matches the currently authenticated user.

### AI Conversation Memory Isolation (FastAPI)
- FastAPI does not manage individual user JWT validation. Instead, the Spring Boot backend validates the user and enriches requests sent to the AI microservice with the user's validated `email`.
- FastAPI's router automatically **scopes the conversation memory** by prepending the validated `email:` to the `conversationId`.
- This scoping prevents malicious actors from accessing another user's AI assistant conversation history by manipulating the `conversationId` parameter. On response, the email prefix is stripped to maintain API contract compliance.

---

## 3. AI Security

### Prompt Injection Mitigation
- System instructions are strictly separated from untrusted user queries and database contexts.
- Prompt construction uses XML-like boundary tags (`<system_instructions>`, `<conversation_history>`, `<retrieved_context>`, `<user_query>`) to encapsulate inputs.
- LLMs are instructed to treat text within `<user_query>` tags as data only, preventing instructions inside query text from hijacking control flow.
- System prompt assembly is validated on startup and request time.

### Model Loading & hot reloads
- Hot reloads via `/models/reload` require an additional `X-Model-Reload-Token` header, which must match the configured `PRICEPILOT_MODEL_RELOAD_TOKEN` environment variable.
- Failures during pickle loading in the `ModelRegistry` are captured safely. Absolute file paths or system usernames are stripped from log outputs to prevent folder path disclosure.

---

## 4. API Security

### Security Headers
Every HTTP response from both the Spring Boot and FastAPI microservices includes the following security headers:
- `X-Content-Type-Options: nosniff` — Prevents mime-type sniffing.
- `X-Frame-Options: DENY` — Prevents clickjacking.
- `Referrer-Policy: strict-origin-when-cross-origin` — Restricts referrer header leaks.
- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none';` — Restricts asset loading and frame nesting.
- `Permissions-Policy: geolocation=(), microphone=(), camera=()` — Disables browser API features not needed by the application.

### Configurable Rate Limiting
IP-based token-bucket rate limiting is enforced on sensitive endpoints:
- **Authentication**: Prevents brute-forcing logins (`pricepilot.rate-limit.auth.limit`).
- **AI Assistant**: Prevents resource exhaust/excessive LLM API cost (`pricepilot.rate-limit.ai.limit`).
- **Recommendations**: Prevents scraping (`pricepilot.rate-limit.recommendation.limit`).

---

## 5. SDK Security

The `pricepilot-python-sdk` is audited to protect client integrations:
- **Header Isolation**: Custom headers passed in client requests are sent directly in the HTTP request call rather than being merged into the persistent `session.headers`, preventing API key or auth token leakage between requests.
- **Exception Sanitization**: Exception messages are sanitized via regular expressions to strip JWT tokens, passwords, or API keys, ensuring credentials are never printed in traces or stdout.

---

## 6. Serialization & Cache Security

To mitigate Remote Code Execution (RCE) via polymorphic deserialization in Redis caching:
- The unsafe `LaissezFaireSubTypeValidator` is replaced by a strict `BasicPolymorphicTypeValidator` whitelist.
- Caching only permits classes under the `com.pricepilot` package, standard java types (`java.util.*`, `java.lang.*`, `java.math.*`, `java.time.*`), and Spring Data domain types.
- Any attempt to deserialize arbitrary non-whitelisted classes results in a validation exception.

---

## 7. Security Logging & Auditing

Security audit logs use a standardized format prefix `AUDIT: [EVENT_TYPE]` to facilitate SIEM ingestion.
Audit events are written for:
- `LOGIN_SUCCESS` (logs email, never password or JWT).
- `LOGIN_FAILURE` (logs email and failure reason).
- `ACCOUNT_LOCK` (triggered when login fails due to locked state).
- `AUTHORIZATION_FAILURE` (logs email, URI, and permission breach).
- `AUTHENTICATION_FAILURE` (logs uri and authentication exception).
- `API_KEY_FAILURE` (triggered on FastAPI when X-API-Key is invalid).
- `RELOAD_TOKEN_FAILURE` (triggered on FastAPI when reloading models with invalid reload token).
- `RATE_LIMIT_EXCEEDED` (logs client IP and category).

---

## 8. Dependency Scanning & NVD API Configuration

A GitHub Actions workflow (`security-scan.yml`) automatically runs dependency vulnerability scans on every push and pull request to `main` or `master`:
- **Backend (Maven)**: Runs OWASP Dependency Check, failing the build if a vulnerability exceeds CVSS score `7.0` (High/Critical).
- **Frontend (npm)**: Runs `npm audit --audit-level=high`.
- **Python (AI Service & SDK)**: Runs `pip-audit` on dependencies.

### NVD API Key Setup for OWASP Dependency Check

- **Why an NVD API Key is Recommended**: Unauthenticated requests to the NIST National Vulnerability Database (NVD) API are strictly rate-limited (5 requests per 30 seconds), causing automated CI workflow failures with HTTP 429 errors. Authenticated requests using an API key increase the rate limit to 50 requests per 30 seconds, ensuring fast and reliable builds.
- **Requesting an API Key**: Obtain a free API key from the NIST NVD Portal: [https://nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key).
- **Adding to GitHub Secrets**:
  1. Go to your GitHub repository: **Settings** -> **Secrets and variables** -> **Actions**.
  2. Click **New repository secret**.
  3. Set **Name**: `NVD_API_KEY`.
  4. Paste the API key value into **Secret** and save.
- **Secret Safety**: API keys must **never** be hardcoded or committed to source control. The workflow accesses the secret dynamically via `${{ secrets.NVD_API_KEY }}`.

---

## 9. Responsible Disclosure

If you discover a security vulnerability in PricePilot, please report it privately:
1. Contact the security team at **security@pricepilot.example.com**.
2. Do not open public GitHub issues or discuss the vulnerability publicly until a patch is released.
3. Provide a clear proof of concept (PoC) to help us verify and resolve the issue quickly.
