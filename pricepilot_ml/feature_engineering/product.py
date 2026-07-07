import pandas as pd
from typing import Optional

class ProductFeatureBuilder:
    """Aggregates raw product dataset and analytics to produce ML-ready product features."""
    def __init__(self) -> None:
        pass

    def build_features(self, df_products: pd.DataFrame, df_analytics: Optional[pd.DataFrame] = None) -> pd.DataFrame:
        df = df_products.copy()
        if df_analytics is not None and not df_analytics.empty:
            # Rename productId to match the product id for merge
            df_analytics_clean = df_analytics.rename(columns={"productId": "id"}).drop_duplicates(subset=["id"])
            df = df.merge(df_analytics_clean, on="id", how="left")
            
        # Fill missing analytics features with 0
        analytics_cols = ["viewCount", "saveCount", "watchlistCount", "priceChangeCount", "trendingScore"]
        for col in analytics_cols:
            if col in df.columns:
                df[col] = df[col].fillna(0.0)
            else:
                df[col] = 0.0

        # Build rating feature (averageSellerRating)
        if "averageSellerRating" in df.columns:
            df["averageSellerRating"] = df["averageSellerRating"].fillna(4.0)
        else:
            df["averageSellerRating"] = 4.0

        if "sellerCount" in df.columns:
            df["sellerCount"] = df["sellerCount"].fillna(0).astype(int)
        else:
            df["sellerCount"] = 0

        # Calculate discount ratio
        if "currentMinPrice" in df.columns and "originalMinPrice" in df.columns:
            denom = df["originalMinPrice"].fillna(0).astype(float)
            diff = (df["originalMinPrice"] - df["currentMinPrice"]).fillna(0).astype(float)
            df["discount_ratio"] = (diff / denom).fillna(0.0)
            df.loc[denom == 0, "discount_ratio"] = 0.0
            
        return df
