<div align="center">

# PricePilot Frontend

### Modern React 19 + TypeScript SPA for Shopping Decision Intelligence

<p align="center">
  <img src="https://img.shields.io/badge/React-19.2.6-cyan.svg?style=flat-square&logo=react" alt="React 19.2">
  <img src="https://img.shields.io/badge/TypeScript-6.0.2-blue.svg?style=flat-square&logo=typescript" alt="TypeScript 6.0">
  <img src="https://img.shields.io/badge/Vite-8.1.5-purple.svg?style=flat-square&logo=vite" alt="Vite 8.1">
  <img src="https://img.shields.io/badge/Tailwind_CSS-4.3.3-38bdf8.svg?style=flat-square&logo=tailwindcss" alt="Tailwind CSS 4.3">
  <img src="https://img.shields.io/badge/Design-Midnight_Intelligence-9585FF.svg?style=flat-square" alt="Midnight Intelligence">
  <img src="https://img.shields.io/badge/Tests-256_Passing-success.svg?style=flat-square" alt="256 Tests Passing">
</p>

<p align="center">
  <a href="../README.md"><strong>📖 Root Documentation</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-design-system-midnight-intelligence"><strong>Design System</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-marketplace-offer-integration-v13"><strong>Marketplace UI</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-local-development"><strong>Local Development</strong></a> &nbsp;&bull;&nbsp;
  <a href="#-testing--verification"><strong>Testing</strong></a>
</p>

</div>

---

> [!IMPORTANT]
> ### 🛡️ Marketplace UI Transparency (v1.3)
> All seller offers rendered on the product detail page originate from **offline seed catalog records** and **simulated demonstration adapters**. The frontend implements explicit visual safeguards: simulated offers display amber tags (`Simulated Offer`, `SIMULATED DEAL (TESTING)`), stale offers display `Lowest Stale Listed Price`, and neither can display an unqualified green `Best Deal` badge.

---

## 🎨 Design System: Midnight Intelligence

The PricePilot frontend implements the **Midnight Intelligence** aesthetic, engineered for visual clarity, data density, and reduced eye strain:

### Theme Tokens (`src/index.css`)

```css
/* Dark Theme (Default) */
--bg-page:          #111116;   /* Deep Midnight background */
--surface-primary:  #202029;   /* Midnight slate card surface */
--surface-accent:   #282733;   /* Interactive hover surface */
--border-subtle:    #393946;   /* Muted structural border */
--accent-primary:   #9585FF;   /* Cyber Iris neon accent */
--text-primary:     #F6F5FA;   /* High-contrast foreground */
--text-secondary:   #C7C5D2;   /* Muted metadata foreground */

/* Light Theme */
--bg-page:          #F6F6FA;   /* Clean cloud backdrop */
--surface-primary:  #FFFFFF;   /* Pure white card surface */
--surface-accent:   #ECE9FF;   /* Soft violet accent surface */
--border-subtle:    #DCDCE5;   /* Light border divider */
--accent-primary:   #6353D9;   /* Deep Iris accent */
--text-primary:     #22222D;   /* Dark foreground */
--text-secondary:   #555565;   /* Secondary slate text */
```

### Theme Control & Persistence
- Managed via [`ThemeContext.tsx`](src/context/ThemeContext.tsx).
- Automatically persists user preference in `localStorage` (`pricepilot_theme`).
- Directly applies `class="dark"` / `class="light"` and `data-theme` attribute to `document.documentElement` for zero-flicker hydration.

---

## 🏛️ Frontend Architecture

```text
frontend/src/
├── assets/              # Static assets (hero.png, icons)
├── components/          # Modular component library
│   ├── alternative/     # Qualified product alternative cards
│   ├── ambient/         # Protected canvas particle background (AmbientBackground.tsx)
│   ├── analytics/       # Price fluctuation charts and trend metrics
│   ├── common/          # ProductImage fallbacks, modal containers, buttons
│   ├── comparison/      # Side-by-side spec comparison table and selector
│   ├── landing/         # Hero section, feature preview cards
│   ├── notifications/   # Toast notification stack
│   ├── recommendation/  # Evidence-grounded recommendation cards
│   ├── search/          # Accessible mobile filter sheets and search bars
│   └── SellerCard.tsx   # v1.3 Normalized marketplace offer card
├── context/             # AuthContext, ThemeContext
├── currency/            # Deterministic multi-currency conversion engine
├── marketplace/         # v1.3 Client marketplace layer
│   ├── index.ts         # Public barrel export
│   ├── offerNormalizer.ts       # DTO to NormalizedOffer mapper and badge rules
│   ├── offerNormalizer.test.ts  # 34 Vitest fake-timer boundary tests
│   └── types.ts                 # Normalized offer client types
├── pages/               # Route views
│   ├── LandingPage.tsx          # Conversational and keyword search entrypoint
│   ├── SearchPage.tsx           # Multi-faceted discovery with filters
│   ├── ProductPage.tsx          # Detail view with multi-seller offers
│   ├── ComparisonPage.tsx       # 2–5 product side-by-side comparison
│   ├── TrendingProductsPage.tsx # Popular catalog demand trends
│   └── ...
├── services/            # Canonical API client layer (api.ts)
└── types/               # TypeScript interfaces and REST DTOs
```

---

## 🛍️ Marketplace Offer Integration (v1.3)

### Offer Pipeline on `ProductPage.tsx`
1. On product load, [`ProductPage.tsx`](src/pages/ProductPage.tsx) executes `apiService.getProductOffers(productId)`.
2. Raw DTOs are mapped into client models using [`mapDtoToNormalizedOffer()`](src/marketplace/offerNormalizer.ts).
3. Offers render in [`SellerCard.tsx`](src/components/SellerCard.tsx) with a user toggle between **Card Grid** and **Detailed Table** view modes.

### Visual Guardrails & Disclosures
- **Honest Provenance Badges**:
  - `Catalog Offer`: Standard database seed offer.
  - `Simulated Offer`: Generated by offline mock adapter.
  - `Catalog Offer (Stale)`: Observations older than 168 hours.
  - `Catalog Offer (Unverified Freshness)`: Missing observation timestamp or future clock skew beyond 5 minutes.
- **Unqualified Deal Protection**:
  - Simulated lowest prices receive amber badge `SIMULATED DEAL (TESTING)` and text `"Lowest simulated base price (Testing)"`.
  - Stale lowest prices receive amber badge `Lowest Stale Listed Price`.
  - The green `Best Deal` badge is **strictly restricted** to fresh, non-simulated listings.
- **Unknown Fee Disclosures**:
  - Every offer card explicitly displays: `"Base price · Fees may apply at checkout"`.
  - Detailed pricing disclaimer displayed: *"Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout."*
- **Offline Fallback Warning**:
  - If `GET /api/v1/products/{id}/offers` fails or is unreachable, the UI displays a prominent amber warning banner:
    > *Marketplace offer aggregator unavailable. Displaying catalog prices as fallback. [Catalog Fallback]*

---

## 📱 Responsive Layout & Viewports

PricePilot is tested and optimized across mobile and desktop viewports:

| Viewport | Target Device | Navigation & Header | Offer Section Layout |
| :--- | :--- | :--- | :--- |
| **Mobile (~390px)** | iPhone 12/13/14, Pixel | Navigation collapsed into slide-out hamburger drawer | Single-column stacked seller cards; touch targets $\ge 40$px |
| **Tablet (~768px)** | iPad, Android tablets | Condensed navigation links | 2-column responsive grid |
| **Desktop (1280px+)** | Laptops & Monitors | Full header navigation bar with currency and theme switches | 3-column card grid or full comparison table view |

---

## 🚀 Local Development

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Configure API Base URL (Optional)
By default, the client points to `http://localhost:8080/api/v1`. To override, create `.env.local`:
```env
VITE_API_URL=http://localhost:8080
```

### 3. Start Development Server
```bash
npm run dev
```
*Application runs at `http://localhost:5173/`.*

---

## 🧪 Testing & Verification

### Running Vitest Suites
```bash
# Run all 256 frontend unit and integration tests
npx vitest run

# Run focused marketplace offer normalizer tests
npx vitest run src/marketplace/offerNormalizer.test.ts

# Run ambient background component tests
npx vitest run src/components/ambient/ambientBackground.test.ts
```

*Latest Audit Results*: **256 passed tests across 27 test files**.

### Production Build Validation
```bash
npm run build
```
Executes `tsc -b && vite build`. Builds minified static bundle in `dist/` in under 1 second.

---

## ⚠️ Known Quality Findings & Exceptions

- **ESLint**: 40 problems reported (34 errors, 6 warnings), primarily pre-existing `react-hooks/set-state-in-effect` patterns. Marketplace additions include 2 lint items in `productOffersIntegration.test.ts` (`no-useless-assignment`, `prefer-const`) and 1 in `ProductPage.tsx` (`setOffersLoading(true)` in effect).
- **Guest Comparison 401**: Visiting `/compare` without logging in triggers `apiService.getSavedComparisons()`, returning HTTP 401. This is caught gracefully in `.catch(() => {})` and does not crash or break the UI.
