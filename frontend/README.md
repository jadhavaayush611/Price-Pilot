<div align="center">

# PricePilot Frontend

### Modern React + TypeScript SPA for Shopping Decision Intelligence

<p align="center">
  <img src="https://img.shields.io/badge/React-19.2-cyan.svg?style=flat-square&logo=react" alt="React 19.2">
  <img src="https://img.shields.io/badge/TypeScript-6.0-blue.svg?style=flat-square&logo=typescript" alt="TypeScript 6.0">
  <img src="https://img.shields.io/badge/Vite-8.1-purple.svg?style=flat-square&logo=vite" alt="Vite 8.1">
  <img src="https://img.shields.io/badge/Tailwind_CSS-4.3-38bdf8.svg?style=flat-square&logo=tailwindcss" alt="Tailwind CSS 4.3">
  <img src="https://img.shields.io/badge/Vitest-4.1-yellow.svg?style=flat-square&logo=vitest" alt="Vitest 4.1">
</p>

<p align="center">
  <a href="https://price-pilot-ch3q.onrender.com"><strong>🚀 Production Web App</strong></a> &nbsp;&bull;&nbsp;
  <a href="../README.md"><strong>📖 Root Documentation</strong></a>
</p>

</div>

---

<h2 align="center">Overview</h2>

The PricePilot frontend is a high-performance Single Page Application (SPA) providing an evidence-grounded consumer shopping interface. It handles natural-language product discovery, interactive multi-product comparison matrices, personalized recommendations, real-time multi-currency pricing, and conversational shopping assistance.

The client acts as a pure presentation layer, consuming structured evidence from the Spring Boot backend API.

---

<h2 align="center">Technology Stack</h2>

| Library / Tool | Version | Purpose |
| :--- | :--- | :--- |
| **React** | 19.2.6 | Component-based UI architecture |
| **TypeScript** | 6.0.2 | End-to-end static typing and contract safety |
| **Vite** | 8.1.5 | High-performance build tooling and local development server |
| **Tailwind CSS** | 4.3.3 | Utility-first styling and dark-mode aesthetic |
| **TanStack Query** | 5.101.3 | Asynchronous server state management and caching |
| **Framer Motion** | 12.42.2 | Fluid page transitions, ambient effects, and micro-interactions |
| **Lucide React** | 1.25.0 | Accessible, lightweight SVG iconography |
| **Axios** | 1.20.0 | Canonical HTTP client with request and response interceptors |
| **Vitest** | 4.1.11 | Fast unit and component integration test runner |

---

<h2 align="center">Architecture & Directory Structure</h2>

```
frontend/src/
├── components/          # Reusable UI components
│   ├── alternative/     # Alternative product cards and lists
│   ├── ambient/         # Ambient cursor motion and glowing background effects
│   ├── analytics/       # Historical price charts, trend cards, and deal badges
│   ├── common/          # Buttons, modals, tables, and ProductImage fallbacks
│   ├── landing/         # Hero section, value proposition, and feature previews
│   ├── layout/          # Navigation header, responsive sidebar, and footer
│   ├── notifications/   # Toast alerts and notification dropdowns
│   ├── recommendation/  # Scored recommendation cards and badges
│   └── ui/              # Primitive design system components
├── context/             # React contexts (AuthContext, session restore)
├── currency/            # Deterministic multi-currency conversion engine
├── pages/               # Top-level route views
│   ├── AiAssistantPage.tsx      # Conversational shopping assistant
│   ├── ComparisonPage.tsx       # 2–5 product side-by-side comparison matrix
│   ├── DashboardPage.tsx        # User overview and quick stats
│   ├── DashboardV2Page.tsx      # Intelligence dashboard with deal alerts
│   ├── PreferencesPage.tsx      # Brand, budget, and priority customization
│   ├── ProductPage.tsx          # Single product details and offer listings
│   ├── SearchPage.tsx           # Multi-faceted and natural-language discovery
│   ├── WatchlistPage.tsx        # Monitored products and price target alerts
│   └── ...
├── services/            # Canonical API client layer (api.ts, api.test.ts)
└── types/               # TypeScript interfaces, DTOs, and domain models
```

---

<h2 align="center">API Integration</h2>

All network communication with the backend is centralized in [`src/services/api.ts`](src/services/api.ts).

### Canonical API Base URL Resolution
The API client resolves its base endpoint at build time via Vite environment variables:

```ts
const API_BASE_URL = getApiBaseUrl();
```

* **Canonical Variable**: `VITE_API_URL` (injected at build time by Vite).
* **Automatic Path Normalization**: Automatically appends `/api/v1` if a root origin is supplied (e.g. `https://pricepilot-backend-4sxk.onrender.com` &rarr; `https://pricepilot-backend-4sxk.onrender.com/api/v1`).
* **Backward Compatibility**: Supports `VITE_API_BASE_URL` as a fallback.
* **Local Fallback**: Defaults to `http://localhost:8080/api/v1` when no environment variables are set.

### Authentication & Interceptors
- **Request Interceptor**: Automatically injects `Authorization: Bearer <token>` from `localStorage` on authenticated requests.
- **Response Interceptor**: Emits non-intrusive latency telemetry in development mode (`import.meta.env.DEV`) and remains silent in production.
- **Authentic Health Checks**: `apiService.checkHealth()` queries `/health` directly without synthetic fallback masks.

---

<h2 align="center">Local Development</h2>

### 1. Install Dependencies
```bash
npm install
```

### 2. Configure Environment (Optional)
For custom backend ports, configure `.env.local`:
```env
VITE_API_URL=http://localhost:8080
```

### 3. Start Development Server
```bash
npm run dev
```
The application will be accessible at `http://localhost:5173/`.

---

<h2 align="center">Testing & Production Build</h2>

```bash
# Run the complete test suite once
npm test -- --run

# Run tests in watch mode
npm test

# Run strict TypeScript check and production bundle
npm run build
```

* **Verified Test Coverage**: **170 / 170 tests passing** across 22 test files.
* **Build Artifact**: Minified static SPA bundle generated into `dist/`.

---

<h2 align="center">Deployment (Render Static Site)</h2>

The frontend is deployed as a **Render Static Site**:

- **Build Command**: `npm run build`
- **Publish Directory**: `dist`
- **Environment Variable**:
  ```env
  VITE_API_URL=https://pricepilot-backend-4sxk.onrender.com
  ```
- **Live URL**: [https://price-pilot-ch3q.onrender.com](https://price-pilot-ch3q.onrender.com)

---

<h2 align="center">Frontend Engineering Conventions</h2>

<table width="100%">
<tr>
<td width="50%" valign="top">

**1. Canonical API Client**<br/>
Never instantiate secondary Axios instances or raw `fetch()` calls. Always import `apiService` or `apiClient` from `src/services/api.ts`.

</td>
<td width="50%" valign="top">

**2. Backend-Owned Intelligence**<br/>
The client never computes ranking scores, deal quality classifications, or eligibility rules. It renders structured evidence returned by the backend.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**3. Deterministic Multi-Currency**<br/>
Always use shared currency helpers (`getDisplayPrice`, `formatPrice`, `convertToUsd`) from `src/currency` rather than ad-hoc math or hardcoded symbols.

</td>
<td width="50%" valign="top">

**4. Safe Image Fallbacks**<br/>
Always utilize `ProductImage` or accessible placeholder handlers to prevent broken image badges when remote merchant CDNs fail.

</td>
</tr>
</table>
