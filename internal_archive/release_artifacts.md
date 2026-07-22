# PricePilot v1.0.1 Final Release Artifact Build

## Git Tag Verification
- **Git Tag**: `v1.0.1`
- **Git Describe Output**: `v1.0.1`
- **Git Commit**: `0e74f1c`
- **Source Code Status**: Unmodified

---

## 1. Backend Release Artifact

- **Build Command**: `./mvnw clean package`
- **Target Location**: `backend/target/pricepilot-1.0.1.jar`
- **Filename**: `pricepilot-1.0.1.jar`
- **Archive Type**: Executable Spring Boot JAR (Repackaged with `BOOT-INF/` dependencies)
- **Version Metadata**: `1.0.1`
- **File Size**: `76,358,578` bytes (~72.82 MB)
- **SHA-256 Checksum**: `C4613AABD3E421BF676F7E44E93D13A86218B79A64EC0FE67C4B402EE3142C8D`
- **Verification Status**: SUCCESS (Passed 74 unit & integration tests)

---

## 2. Frontend Release Artifact

- **Build Commands**: `npm ci` & `npm run build`
- **Output Directory**: `frontend/dist/`
- **Production Build Status**: SUCCESS (Built in 5.28s, 2315 modules transformed)
- **Bundle Metrics**:
  - **JS/CSS Production Assets Size**: `771,667` bytes (~771.67 KB)
  - **Total `dist/` Directory Size**: `3,652,719` bytes (~3.65 MB, including sourcemaps & static assets)

### Generated Assets Listing
- `dist/index.html` (0.71 kB)
- `dist/assets/index-DwDrJfo5.css` (80.49 kB)
- `dist/assets/rolldown-runtime-CNC7AqOf.js` (0.87 kB)
- `dist/assets/Table-Bx8OEtxG.js` (4.28 kB)
- `dist/assets/LoginPage-DBm74kbi.js` (4.39 kB)
- `dist/assets/RegisterPage-DFfYmpei.js` (6.10 kB)
- `dist/assets/SavedProductsPage-sKk2Jy6Q.js` (6.74 kB)
- `dist/assets/TrendingProductsPage-_gBUaIRs.js` (7.78 kB)
- `dist/assets/SellerManagementPage-WPGgqWh7.js` (11.71 kB)
- `dist/assets/ProductManagementPage-BXd_Umpx.js` (13.04 kB)
- `dist/assets/AiAssistantPage-DBPj2JDK.js` (14.34 kB)
- `dist/assets/WatchlistPage-CHl5RV3s.js` (15.30 kB)
- `dist/assets/PriceManagementPage-CG1Z8spk.js` (16.49 kB)
- `dist/assets/SearchPage-D0CTRPAe.js` (16.85 kB)
- `dist/assets/LandingPage-7vqS18TZ.js` (17.12 kB)
- `dist/assets/index-G7QEue0N.js` (27.37 kB)
- `dist/assets/DashboardPage-_xr2_E5V.js` (29.65 kB)
- `dist/assets/ProductPage-AKkpzmjt.js` (39.63 kB)
- `dist/assets/vendor-utils-C0bYvdnr.js` (44.71 kB)
- `dist/assets/vendor-ui-JKP5OE32.js` (124.91 kB)
- `dist/assets/vendor-react-D17NPEKd.js` (274.55 kB)

---

## 3. Python SDK Release Artifacts

- **Build Command**: `uv build`
- **Output Directory**: `pricepilot-python-sdk/dist/`
- **Build Status**: SUCCESS

### Generated Artifact Details
1. **Wheel Distribution**:
   - **Filename**: `pricepilot_sdk-1.0.1-py3-none-any.whl`
   - **File Size**: `22,178` bytes
   - **SHA-256 Checksum**: `3AE525F1373B5E39D0C028B2D0CF22AE2B24989CD9BB425289C7AE0E676EF236`

2. **Source Distribution (sdist)**:
   - **Filename**: `pricepilot_sdk-1.0.1.tar.gz`
   - **File Size**: `55,087` bytes
   - **SHA-256 Checksum**: `53D65863A659FCF8FADA96E29D6258D1A62207FD7B08789151F77E2BED1AEE49`

---

## 4. Docker Images

Container build skipped because Docker daemon was unavailable.

---

## 5. Artifact Checksum Table

| Component | Filename | Size (Bytes) | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Backend** | `pricepilot-1.0.1.jar` | 76,358,578 | `C4613AABD3E421BF676F7E44E93D13A86218B79A64EC0FE67C4B402EE3142C8D` |
| **Python SDK (Wheel)** | `pricepilot_sdk-1.0.1-py3-none-any.whl` | 22,178 | `3AE525F1373B5E39D0C028B2D0CF22AE2B24989CD9BB425289C7AE0E676EF236` |
| **Python SDK (sdist)** | `pricepilot_sdk-1.0.1.tar.gz` | 55,087 | `53D65863A659FCF8FADA96E29D6258D1A62207FD7B08789151F77E2BED1AEE49` |

---

## Final Verification
- [x] Confirmed Git branch/tag: `git checkout v1.0.1` and `git describe --tags` returns `v1.0.1`.
- [x] No source code was modified during this release build process.
- [x] All build artifacts originate cleanly from Git tag `v1.0.1`.
