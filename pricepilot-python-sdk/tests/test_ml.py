import os
import sys
import pytest
import pandas as pd
import numpy as np
from datetime import datetime

# Inject workspace path so pricepilot_ml can be imported
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from pricepilot_ml.preprocessing import (
    MissingValueHandler,
    DuplicateRemoval,
    OutlierDetector,
    CategoricalEncoder,
    PriceNormalizer,
    FeatureScaler,
    TimestampParser,
    NullSafety
)
from pricepilot_ml.feature_engineering import (
    ProductFeatureBuilder,
    UserFeatureBuilder,
    InteractionFeatureBuilder,
    RecommendationFeatureBuilder
)

def test_missing_value_handler():
    df = pd.DataFrame({
        "num": [1.0, 2.0, None, 4.0],
        "cat": ["A", "B", "A", None]
    })
    handler = MissingValueHandler(numeric_strategy="median", categorical_strategy="constant", fill_value="Missing")
    df_clean = handler.fit_transform(df, numeric_cols=["num"], categorical_cols=["cat"])
    
    assert float(df_clean.loc[2, "num"]) == 2.0 # median of 1, 2, 4 is 2
    assert df_clean.loc[3, "cat"] == "Missing"

def test_duplicate_removal():
    df = pd.DataFrame({"id": [1, 2, 2, 3]})
    df_clean = DuplicateRemoval.remove(df, subset=["id"])
    assert len(df_clean) == 3

def test_outlier_detector():
    df = pd.DataFrame({"val": [1, 2, 3, 4, 100]}) # 100 is an outlier
    detector = OutlierDetector(factor=1.5)
    df_clean = detector.fit_transform(df, cols=["val"], strategy="cap")
    assert df_clean.loc[4, "val"] < 10.0 # Capped

def test_categorical_encoder():
    df = pd.DataFrame({"cat": ["apple", "banana", "apple", "cherry"]})
    encoder = CategoricalEncoder()
    df_clean = encoder.fit_transform(df, cols=["cat"])
    assert df_clean.loc[0, "cat"] == df_clean.loc[2, "cat"]
    assert df_clean.loc[1, "cat"] != df_clean.loc[3, "cat"]

def test_price_normalizer():
    df = pd.DataFrame({"price": [99.0, 199.0]})
    df_clean = PriceNormalizer.log_normalize(df, cols=["price"])
    assert df_clean.loc[0, "price"] == np.log1p(99.0)

def test_feature_scaler():
    df = pd.DataFrame({"val": [1.0, 2.0, 3.0]})
    scaler = FeatureScaler(strategy="minmax")
    df_clean = scaler.fit_transform(df, cols=["val"])
    assert df_clean.loc[0, "val"] == 0.0
    assert df_clean.loc[2, "val"] == 1.0

def test_timestamp_parser():
    df = pd.DataFrame({"date": ["2026-07-07T14:30:00", "2026-07-11T09:00:00"]}) # 2026-07-11 is Saturday
    df_clean = TimestampParser.parse(df, col="date")
    assert df_clean.loc[0, "date_hour"] == 14
    assert df_clean.loc[0, "date_is_weekend"] == 0
    assert df_clean.loc[1, "date_is_weekend"] == 1

def test_null_safety():
    df = pd.DataFrame({"col_a": [1, None]})
    df_clean = NullSafety.ensure(df, {"col_a": 0, "col_b": "default"})
    assert df_clean.loc[1, "col_a"] == 0
    assert df_clean.loc[0, "col_b"] == "default"

def test_product_feature_builder():
    df_products = pd.DataFrame({
        "id": ["p1", "p2"],
        "name": ["Prod 1", "Prod 2"],
        "currentMinPrice": [10.0, 20.0],
        "originalMinPrice": [15.0, 20.0]
    })
    df_analytics = pd.DataFrame({
        "productId": ["p1"],
        "viewCount": [50],
        "trendingScore": [12.0]
    })
    
    builder = ProductFeatureBuilder()
    df_features = builder.build_features(df_products, df_analytics)
    assert df_features.loc[0, "viewCount"] == 50
    assert df_features.loc[1, "viewCount"] == 0
    assert df_features.loc[0, "discount_ratio"] == (15.0 - 10.0) / 15.0

def test_user_feature_builder():
    df_dashboard = pd.DataFrame({
        "userId": ["u1"],
        "email": ["user@example.com"],
        "savedCount": [5]
    })
    df_interactions = pd.DataFrame({
        "userId": ["u1", "u1"],
        "productId": ["p1", "p2"],
        "interactionType": ["PRODUCT_VIEW", "PRODUCT_VIEW"],
        "currentMinPrice": [100.0, 200.0]
    })
    df_products = pd.DataFrame({
        "id": ["p1", "p2"],
        "category": ["Shoes", "Shoes"],
        "brand": ["Nike", "Adidas"]
    })
    
    builder = UserFeatureBuilder()
    df_features = builder.build_features(df_dashboard, df_interactions, df_products)
    assert df_features.loc[0, "preferredCategory"] == "Shoes"
    assert df_features.loc[0, "averagePriceViewed"] == 150.0

def test_interaction_feature_builder():
    df_interactions = pd.DataFrame({
        "userId": ["u1", "u1"],
        "productId": ["p1", "p2"],
        "createdAt": ["2026-07-07T12:00:00", "2026-07-07T12:05:00"],
        "metadataJson": ['{"keyword": "sports"}', '']
    })
    
    builder = InteractionFeatureBuilder()
    df_features = builder.build_features(df_interactions)
    assert df_features.loc[0, "hour"] == 12
    assert df_features.loc[0, "search_keyword"] == "sports"
    assert df_features.loc[1, "session_length"] == 2

def test_recommendation_feature_builder():
    df_user = pd.DataFrame({
        "userId": ["u1"],
        "preferredCategory": ["Shoes"],
        "preferredBrand": ["Nike"]
    })
    df_product = pd.DataFrame({
        "id": ["p1"],
        "category": ["Shoes"],
        "brand": ["Adidas"]
    })
    
    builder = RecommendationFeatureBuilder()
    df_features = builder.build_features(df_user, df_product)
    assert df_features.loc[0, "category_match"] == 1
    assert df_features.loc[0, "brand_match"] == 0

from pricepilot_ml.datasets import DatasetExporter

def test_dataset_exporter(tmp_path):
    df = pd.DataFrame({"col": [1, 2, 3]})
    exporter = DatasetExporter(base_dir=str(tmp_path))
    csv_path, meta_path = exporter.export(
        df=df,
        name="test_data",
        stage="raw",
        version="1.0.0",
        schema_version="1.0",
        generated_by="test-agent",
        future_training_target="target_col"
    )
    
    assert os.path.exists(csv_path)
    assert os.path.exists(meta_path)
    
    with open(meta_path) as f:
        import json
        meta = json.load(f)
        assert meta["dataset_version"] == "1.0.0"
        assert meta["record_count"] == 3
        assert meta["future_training_target"] == "target_col"
