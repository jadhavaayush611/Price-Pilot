import os
import pandas as pd
import numpy as np
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, Tuple
from pricepilot_ml.preprocessing import MissingValueHandler, DuplicateRemoval
from pricepilot_ml.feature_engineering import (
    ProductFeatureBuilder,
    UserFeatureBuilder,
    InteractionFeatureBuilder,
    RecommendationFeatureBuilder
)
from pricepilot_ml.datasets.versioning import DatasetExporter

class DatasetBuilder:
    """Loads raw datasets, applies the cleaning and feature engineering pipelines, and exports processed data."""

    def __init__(self, base_dir: str = ".") -> None:
        self.base_dir = os.path.abspath(base_dir)
        self.exporter = DatasetExporter(base_dir=self.base_dir)

    def generate_synthetic_raw_data(self) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """Generates synthetic datasets when raw files are not found (enables instant local run)."""
        np.random.seed(42)
        n_products = 50
        n_users = 20
        n_interactions = 200

        # Products
        categories = ["Electronics", "Books", "Clothing", "Home", "Sports"]
        brands = ["BrandA", "BrandB", "BrandC", "BrandD", "BrandE"]
        product_ids = [f"prod_{i}" for i in range(1, n_products + 1)]
        df_products = pd.DataFrame({
            "id": product_ids,
            "name": [f"Product {i}" for i in range(1, n_products + 1)],
            "category": np.random.choice(categories, n_products),
            "brand": np.random.choice(brands, n_products),
            "description": [f"Description for product {i}" for i in range(1, n_products + 1)],
            "imageUrl": [f"http://example.com/prod_{i}.png" for i in range(1, n_products + 1)],
            "currentMinPrice": np.random.uniform(10.0, 500.0, n_products),
            "originalMinPrice": np.random.uniform(15.0, 600.0, n_products),
            "averageSellerRating": np.random.uniform(3.0, 5.0, n_products),
            "sellerCount": np.random.randint(1, 5, n_products),
            "viewCount": np.random.randint(0, 1000, n_products).astype(float),
            "saveCount": np.random.randint(0, 100, n_products).astype(float),
            "watchlistCount": np.random.randint(0, 50, n_products).astype(float),
            "priceChangeCount": np.random.randint(0, 10, n_products).astype(float),
            "trendingScore": np.random.uniform(0.0, 100.0, n_products)
        })
        # Ensure originalMinPrice >= currentMinPrice
        df_products["originalMinPrice"] = df_products[["originalMinPrice", "currentMinPrice"]].max(axis=1)

        # Users (Dashboards)
        user_ids = [f"user_{i}" for i in range(1, n_users + 1)]
        df_users = pd.DataFrame({
            "userId": user_ids,
            "minPriceViewed": np.random.uniform(5.0, 50.0, n_users),
            "maxPriceViewed": np.random.uniform(100.0, 1000.0, n_users),
            "preferredCategory": np.random.choice(categories, n_users),
            "preferredBrand": np.random.choice(brands, n_users)
        })

        # Interactions
        interaction_types = ["PRODUCT_VIEW", "SELLER_CLICK", "PRODUCT_SAVE", "WATCHLIST_ADD"]
        base_time = datetime.now(timezone.utc) - timedelta(days=30)
        df_interactions = pd.DataFrame({
            "userId": np.random.choice(user_ids, n_interactions),
            "productId": np.random.choice(product_ids, n_interactions),
            "interactionType": np.random.choice(interaction_types, n_interactions, p=[0.6, 0.2, 0.1, 0.1]),
            "createdAt": [(base_time + timedelta(hours=int(i))).isoformat().replace("+00:00", "Z") for i in range(n_interactions)],
            "metadataJson": [None] * n_interactions
        })

        return df_products, df_users, df_interactions

    def build(self, dataset_version: str = "1.0.0") -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """Runs loading, schema verification, cleaning, feature engineering, and exports datasets."""
        # 1. Load raw data
        raw_prod_path = os.path.join(self.base_dir, "datasets", "raw", f"products_v{dataset_version}.csv")
        raw_user_path = os.path.join(self.base_dir, "datasets", "raw", f"users_v{dataset_version}.csv")
        raw_int_path = os.path.join(self.base_dir, "datasets", "raw", f"interactions_v{dataset_version}.csv")

        if os.path.exists(raw_prod_path) and os.path.exists(raw_user_path) and os.path.exists(raw_int_path):
            df_prod = pd.read_csv(raw_prod_path)
            df_user = pd.read_csv(raw_user_path)
            df_int = pd.read_csv(raw_int_path)
        else:
            # Fallback
            df_prod, df_user, df_int = self.generate_synthetic_raw_data()
            # Export raw copies
            self.exporter.export(df_prod, "products", "raw", dataset_version, "1.0", "DatasetBuilder")
            self.exporter.export(df_user, "users", "raw", dataset_version, "1.0", "DatasetBuilder")
            self.exporter.export(df_int, "interactions", "raw", dataset_version, "1.0", "DatasetBuilder")

        # 2. Schema Validation / Basic Cleaning
        df_prod = DuplicateRemoval.remove(df_prod, subset=["id"] if "id" in df_prod.columns else ["productId"])
        df_user = DuplicateRemoval.remove(df_user, subset=["userId"])
        
        # Missing values handler
        mvh_prod = MissingValueHandler(numeric_strategy="median", categorical_strategy="constant", fill_value="Unknown")
        mvh_prod.fit(df_prod, ["currentMinPrice", "originalMinPrice", "averageSellerRating"], ["category", "brand"])
        df_prod_clean = mvh_prod.transform(df_prod)

        mvh_user = MissingValueHandler(numeric_strategy="median", categorical_strategy="constant", fill_value="Unknown")
        mvh_user.fit(df_user, ["minPriceViewed", "maxPriceViewed"], ["preferredCategory", "preferredBrand"])
        df_user_clean = mvh_user.transform(df_user)

        # 3. Feature Engineering Pipeline
        prod_fe = ProductFeatureBuilder()
        df_prod_features = prod_fe.build_features(df_prod_clean)

        user_fe = UserFeatureBuilder()
        df_user_features = user_fe.build_features(df_user_clean, df_int, df_prod_clean)

        int_fe = InteractionFeatureBuilder()
        df_int_features = int_fe.build_features(df_int, df_prod_clean)

        rec_fe = RecommendationFeatureBuilder()
        df_rec_features = rec_fe.build_features(df_user_features, df_prod_features)

        # 4. Save processed feature sets
        self.exporter.export(df_prod_features, "product_features", "feature_sets", dataset_version, "1.0", "DatasetBuilder")
        self.exporter.export(df_user_features, "user_features", "feature_sets", dataset_version, "1.0", "DatasetBuilder")
        self.exporter.export(df_int_features, "interaction_features", "feature_sets", dataset_version, "1.0", "DatasetBuilder")
        self.exporter.export(df_rec_features, "recommendation_features", "feature_sets", dataset_version, "1.0", "DatasetBuilder")

        return df_prod_features, df_user_features, df_int_features, df_rec_features
