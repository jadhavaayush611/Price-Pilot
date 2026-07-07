# PricePilot — Data Engineering & ML Dataset Pipeline

This document defines the architecture, data flows, and usage guidelines for the PricePilot Data Engineering and Machine Learning dataset pipeline. The pipeline transforms raw transactional and behavior data from PostgreSQL into versioned, clean, and engineered features ready for Machine Learning models.

---

## 1. High-Level Architecture

The pipeline follows a modular architecture from database extraction to versioned file storage:

```mermaid
graph TD
    subgraph PostgreSQL Database
        db_users[(users)]
        db_products[(products)]
        db_analytics[(product_analytics)]
        db_events[(user_interaction_events)]
        db_watchlists[(price_watchlists)]
        db_saved[(saved_products)]
        db_history[(price_histories)]
    end

    subgraph Spring Boot Backend
        repo[JPA Repositories & Specifications]
        service[DatasetServiceImpl]
        controller[DatasetController]
        
        db_users & db_products & db_analytics & db_events & db_watchlists & db_saved & db_history --> repo
        repo --> service
        service --> controller
    end

    subgraph Python SDK Layer
        client[PricePilotClient]
        datasets[DatasetsModule]
        
        controller -- "REST HTTP (JSON / CSV)" --> datasets
        datasets --> client
    end

    subgraph Feature Engineering & ML Pipeline (pricepilot_ml)
        prep[Data Cleaning & Preprocessing]
        fe[Feature Engineering Builders]
        ex[DatasetExporter]
        
        client -- "Pandas DataFrames" --> prep
        prep --> fe
        fe --> ex
        ex --> filesystem[(datasets/)]
    end
```

---

## 2. Part 1 — Dataset Export API (Backend)

The backend provides 8 secure REST endpoints under `/api/v1/datasets/**` to extract normalized tables. These endpoints are restricted to `ADMIN` users.

### Endpoints
1. `GET /api/v1/datasets/products` — Product data including aggregated price bounds and seller counts.
2. `GET /api/v1/datasets/product-analytics` — Product view counts, save counts, watchlists, and trending scores.
3. `GET /api/v1/datasets/interaction-events` — Full clickstream behavior (views, saves, clicks, searches).
4. `GET /api/v1/datasets/watchlists` — Watchlist items, targets, and active alert statuses.
5. `GET /api/v1/datasets/saved-products` — Favorite/saved product timestamps.
6. `GET /api/v1/datasets/search-history` — Keywords and user search queries extracted from interaction logs.
7. `GET /api/v1/datasets/dashboard-summary` — Aggregated user-level activity profiles.
8. `GET /api/v1/datasets/price-history` — Historical price fluctuations across all products and sellers.

### Query Parameters
- **Pagination**: `page` (int), `size` (int), `sort` (comma-separated string, e.g. `createdAt,desc`)
- **Filters**: Filtering parameters vary per endpoint (e.g. `category`, `brand`, `userId`, `productId`, `active`, `role`, `keyword`).
- **Date Ranges**: `startDate` and `endDate` (ISO-8601 datetimes) filter logs based on creation/modification dates.
- **Export Format**: `format` (`json` or `csv`). If `csv` is requested, the backend streams a tabular CSV file attachment.

---

## 3. Part 2 — Python SDK Integration

The Python SDK exposes a unified namespace `client.datasets` to query the backend endpoints directly into memory or save them.

### Raw Data Extraction
```python
from pricepilot import PricePilotClient

# Initialize Client
client = PricePilotClient(base_url="http://localhost:8080/api/v1")
client.auth.login(email="admin@example.com", password="admin123")

# Fetch raw JSON response (paginated)
products_json = client.datasets.get_products(category="Electronics", page=0, size=50)

# Download CSV data directly
products_csv_str = client.datasets.get_products(category="Electronics", format="csv")
```

### Automatic Pandas DataFrame Conversion
The SDK provides automatic pagination wrapping to construct a complete Pandas DataFrame of the entire matching dataset:
```python
# Fetches all pages recursively under-the-hood and joins them
df_products = client.datasets.products_dataframe(category="Electronics")
df_events = client.datasets.interaction_events_dataframe(start_date="2026-07-01T00:00:00")
```

---

## 4. Part 3 & 4 — Preprocessing & Feature Engineering (`pricepilot_ml`)

A dedicated module `pricepilot_ml` implements object-oriented transformers to clean raw data and engineer high-value features.

```
pricepilot_ml/
├── __init__.py
├── datasets/
│   ├── __init__.py
│   └── versioning.py            # DatasetExporter utility
├── feature_engineering/
│   ├── __init__.py
│   ├── product.py               # ProductFeatureBuilder
│   ├── user.py                  # UserFeatureBuilder
│   ├── interaction.py           # InteractionFeatureBuilder
│   └── recommendation.py        # RecommendationFeatureBuilder
└── preprocessing/
    ├── __init__.py
    └── cleaning.py              # MissingValueHandler, OutlierDetector, etc.
```

### Data Preprocessing Utilities (`pricepilot_ml.preprocessing`)
- **`MissingValueHandler`**: Fills missing numeric features with medians/means and categorical values with mode/constants.
- **`DuplicateRemoval`**: Drops duplicates from custom subsets of columns.
- **`OutlierDetector`**: Uses the Interquartile Range (IQR) method to clip or drop outliers in numeric columns.
- **`CategoricalEncoder`**: Implements map-based label encoding for string values.
- **`PriceNormalizer`**: Standardizes price ranges using a log transform (`np.log1p`).
- **`FeatureScaler`**: Implements Standard (Z-score) or MinMax scaling strategies.
- **`TimestampParser`**: Extracts hour, day of week, and weekend indicators from dates.
- **`NullSafety`**: Asserts schema matching and fills default fallback values.

### Feature Builders (`pricepilot_ml.feature_engineering`)
- **`ProductFeatureBuilder`**: Combines product listings with view analytics to construct metrics like `discount_ratio`, `trendingScore`, and imputed ratings.
- **`UserFeatureBuilder`**: Processes activity logs to resolve `preferredCategory`, `preferredBrand`, and price bracket bounds viewed.
- **`InteractionFeatureBuilder`**: Translates clickstreams into parsed temporal indicators, extracts keyword searches, and groups events into sessions using a 30-minute idle threshold.
- **`RecommendationFeatureBuilder`**: Pairs user engagement profiles with product attributes to compute `category_match`, `brand_match`, and price boundary matching metrics.

---

## 5. Part 5 — Dataset Versioning & Lifecycle

Every exported file is saved in a structured repository containing a `.csv` data table and a companion `_metadata.json` metadata document.

### Repository Structure
```
datasets/
├── raw/             # Unprocessed extracts from REST endpoints
├── processed/       # Cleaned, imputed, and scaled tables
├── feature_sets/    # Merged tables containing engineered feature vectors
└── exports/         # Final model input artifacts (train/val/test splits)
```

### Metadata Template
An export of `recommendations_features_v1.0.0_metadata.json` looks like this:
```json
{
    "dataset_version": "1.0.0",
    "export_timestamp": "2026-07-07T15:20:10.123456Z",
    "record_count": 5238,
    "schema_version": "1.0",
    "generated_by": "example-pipeline",
    "future_training_target": "category_match"
}
```

---

## 6. Pipeline Example

The following script summarizes the complete pipeline from download to feature storage (as demonstrated in `examples/recommendation_dataset.ipynb`):

```python
import pandas as pd
from pricepilot import PricePilotClient
from pricepilot_ml.preprocessing import MissingValueHandler, OutlierDetector
from pricepilot_ml.feature_engineering import ProductFeatureBuilder, UserFeatureBuilder, RecommendationFeatureBuilder
from pricepilot_ml.datasets import DatasetExporter

# 1. Fetch raw data
client = PricePilotClient()
client.auth.login(email="admin@example.com", password="admin123")

df_products = client.datasets.products_dataframe()
df_analytics = client.datasets.product_analytics_dataframe()
df_dashboard = client.datasets.dashboard_summary_dataframe()

# Save raw dataset
exporter = DatasetExporter(base_dir=".")
exporter.export(df_products, "products", "raw", "1.0.0", "1.0", "sdk-client")

# 2. Clean & Preprocess
imputer = MissingValueHandler(numeric_strategy="median")
df_products_clean = imputer.fit_transform(
    df_products, 
    numeric_cols=["currentMinPrice", "originalMinPrice", "averageSellerRating"], 
    categorical_cols=["brand", "category"]
)

# 3. Engineer Features
prod_fe = ProductFeatureBuilder().build_features(df_products_clean, df_analytics)
user_fe = UserFeatureBuilder().build_features(df_dashboard)

# 4. Generate Recommendation Candidates
rec_fe = RecommendationFeatureBuilder().build_features(user_fe, prod_fe)

# 5. Export processed features for training
exporter.export(
    df=rec_fe,
    name="recommendation_candidates",
    stage="feature_sets",
    version="1.0.0",
    schema_version="1.0",
    generated_by="ml-pipeline",
    future_training_target="category_match"
)
```
