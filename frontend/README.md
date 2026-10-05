# PricePilot Frontend

Modern, responsive React and TypeScript Single Page Application (SPA) for the PricePilot shopping decision platform.

---

## Overview

The PricePilot frontend delivers an evidence-grounded consumer shopping experience, featuring natural-language product discovery, interactive multi-product comparison matrices, personalized recommendations, real-time multi-currency pricing, and a conversational shopping assistant.

The frontend acts as a pure presentation and interaction layer, consuming structured evidence from the Spring Boot backend API.

---

## Technology Stack

| Library / Tool | Version | Purpose |
| :--- | :--- | :--- |
| **React** | 19.2.6 | Component-based UI architecture |
| **TypeScript** | 6.0.2 | End-to-end static typing and contract safety |
| **Vite** | 8.1.5 | High-performance build tool and local dev server |
| **Tailwind CSS** | 4.3.3 | Modern styling and dark-mode aesthetic |
| **TanStack Query** | 5.101.3 | Asynchronous server state management and caching |
| **Framer Motion** | 12.42.2 | Fluid transitions, ambient cursor motion, and micro-interactions |
| **Lucide React** | 1.25.0 | Accessible, lightweight SVG iconography |
| **Axios** | 1.20.0 | Canonical HTTP client with request/response interceptors |
| **Vitest** | 4.1.11 | Fast unit and component integration test runner |

---

## Architecture & Directory Structure

```
frontend/src/
├── components/          # Reusable UI components
│   ├── alternative/     # Alternative product cards and lists
│   ├── ambient/         # Ambient glowing backgrounds and cursor effects
│   ├── analytics/       # Historical price charts and trend badges
│   ├── common/          # Buttons, modals, tables, and ProductImage fallbacks
│   ├── landing/         # Hero section, feature previews, and callouts
│   ├── layout/          # Navigation header, sidebar, and footer
│   ├── notifications/   # Toast alerts and notification bells
│   ├── recommendation/  # Scored recommendation cards and badges
│   └── ui/              # Primitive design system components
├── context/             # Global React contexts
│   ├── AuthContext.tsx  # JWT authentication, user profile, and session restore
│   └── ...
├── currency/            # Deterministic multi-currency conversion engine
│   ├── index.ts         # Currency rates, formatters, and storage persistence
│   └── currency.test.ts # Conversion and formatting test suite
├── pages/               # Top-level route views
│   ├── AiAssistantPage.tsx      # Grounded conversational shopping assistant
│   ├── ComparisonPage.tsx       # 2–5 product side-by-side comparison matrix
│   ├── DashboardPage.tsx        # User overview and quick stats
│   ├── DashboardV2Page.tsx      # Intelligence dashboard with deal alerts
│   ├── PreferencesPage.tsx      # Brand, budget, and priority customization
│   ├── ProductPage.tsx          # Single product details and offer listings
│   ├── SearchPage.tsx           # Multi-faceted and natural-language discovery
│   ├── WatchlistPage.tsx        # Monitored products and price target alerts
│   └── ...
├── services/            # Canonical API client layer
│   ├── api.ts           # Axios instance, interceptors, and apiService façade
│   ├── api.test.ts      # URL normalization and error parsing unit tests
│   └── assistantTitle.ts# Automated conversation title formatting
└── types/               # TypeScript interfaces, DTOs, and domain models
    └── index.ts         # Product, Price, Seller, User, Assistant, and Analytics types
```

---

## API Integration

All network communication with the backend is centralized in [`src/services/api.ts`](src/services/api.ts).

### Canonical API Base URL Resolution
The API client resolves its base endpoint at build time via Vite environment variables:

```ts
const API_BASE_URL = getApiBaseUrl();
```

* **Canonical Variable**: `VITE_API_URL` (injected at build time by Vite).
* **Automatic Normalization**: Automatically appends `/api/v1` if a root origin is supplied (e.g. `https://pricepilot-backend-4sxk.onrender.com` &rarr; `https://pricepilot-backend-4sxk.onrender.com/api/v1`).
* **Backward Compatibility**: Supports `VITE_API_BASE_URL` as a fallback.
* **Local Fallback**: Defaults to `http://localhost:8080/api/v1` when no environment variables are set.

### Authentication & Interceptors
- **Request Interceptor**: Automatically attaches the JWT token from `localStorage.getItem('token')` as an `Authorization: Bearer <token>` header on every outgoing request.
- **Response Interceptor**: Emits non-intrusive latency telemetry in development mode (`import.meta.env.DEV`) and remains completely silent in production.
- **Authentic Health Checks**: `apiService.checkHealth()` queries `/health` directly without synthetic fallback masks, reflecting the true backend and database health.

---

## Local Development

### 1. Install Dependencies
```bash
npm install
```

### 2. Configure Environment (Optional)
For custom backend ports, create a local `.env.local` file:
```env
VITE_API_URL=http://localhost:8080
```

### 3. Start Development Server
```bash
npm run dev
```
The application will be accessible at `http://localhost:5173/`.

---

## Testing & Quality

PricePilot frontend maintains comprehensive test coverage across component behavior, currency formatting, error parsing, and API contracts:

```bash
# Run the complete test suite once
npm test -- --run

# Run tests in watch mode
npm test
```

* **Current Verified Test Count**: **170 / 170 tests passing** across 22 test files.

---

## Production Build

To verify TypeScript types and generate the optimized static production bundle:

```bash
npm run build
```

This executes `tsc -b` (strict TypeScript validation) followed by `vite build`, producing minified static assets in the `dist/` directory.

---

## Deployment (Render Static Site)

The frontend is deployed as a **Render Static Site**:

- **Build Command**: `npm run build`
- **Publish Directory**: `dist`
- **Environment Variable**:
  ```env
  VITE_API_URL=https://pricepilot-backend-4sxk.onrender.com
  ```
- **Live URL**: [https://price-pilot-ch3q.onrender.com](https://price-pilot-ch3q.onrender.com)

---

## Frontend Engineering Conventions

1. **Use Canonical API Client**: Never create secondary Axios instances, raw `fetch()` calls, or hardcoded URLs in components. Always import `apiService` or `apiClient` from [`src/services/api.ts`](src/services/api.ts).
2. **Backend-Owned Intelligence**: The frontend never independently computes ranking scores, deal quality classifications, or eligibility rules. It renders structured evidence returned by the backend.
3. **Deterministic Multi-Currency**: Always use the shared currency utilities (`getDisplayPrice`, `formatPrice`, `convertToUsd`) from [`src/currency`](src/currency) rather than hardcoded currency symbols or ad-hoc math.
4. **Safe Image Degradation**: Always utilize [`ProductImage`](src/components/common/ProductImage.tsx) or safe placeholder handlers to prevent broken image badges when remote merchant CDNs fail.
5. **Session & Auth Lifecycle**: Manage authentication state strictly through the [`useAuth()`](src/context/AuthContext.tsx) hook.
