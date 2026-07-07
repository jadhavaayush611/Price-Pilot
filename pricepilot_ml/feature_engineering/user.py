import pandas as pd
from typing import Optional

class UserFeatureBuilder:
    """Builds user preference profiles and engagement features from dashboard and interaction history."""
    def __init__(self) -> None:
        pass

    def build_features(
        self,
        df_dashboard: pd.DataFrame,
        df_interactions: Optional[pd.DataFrame] = None,
        df_products: Optional[pd.DataFrame] = None
    ) -> pd.DataFrame:
        df = df_dashboard.copy()
        
        # Build category/brand preference and price range metrics from interaction logs
        if df_interactions is not None and not df_interactions.empty and df_products is not None and not df_products.empty:
            merged = df_interactions.merge(df_products.rename(columns={"id": "productId"}), on="productId", how="inner")
            
            # 1. Preferred Categories (most views)
            cat_counts = merged[merged["interactionType"] == "PRODUCT_VIEW"].groupby(["userId", "category"]).size().reset_index(name="view_count")
            if not cat_counts.empty:
                pref_cats = cat_counts.sort_values("view_count", ascending=False).drop_duplicates(subset=["userId"])
                pref_cats = pref_cats.rename(columns={"category": "preferredCategory"}).drop(columns=["view_count"])
                df = df.merge(pref_cats, on="userId", how="left")
                
            # 2. Preferred Brands (most views)
            brand_counts = merged[merged["interactionType"] == "PRODUCT_VIEW"].groupby(["userId", "brand"]).size().reset_index(name="view_count")
            if not brand_counts.empty:
                pref_brands = brand_counts.sort_values("view_count", ascending=False).drop_duplicates(subset=["userId"])
                pref_brands = pref_brands.rename(columns={"brand": "preferredBrand"}).drop(columns=["view_count"])
                df = df.merge(pref_brands, on="userId", how="left")
                
            # 3. Average, Min, and Max price range viewed
            price_cols_list = ["currentMinPrice"]
            available_cols = [c for c in price_cols_list if c in merged.columns]
            if available_cols:
                price_col = available_cols[0]
                view_prices = merged[merged["interactionType"] == "PRODUCT_VIEW"].groupby("userId")[price_col].agg(["mean", "min", "max"]).reset_index()
                view_prices = view_prices.rename(columns={"mean": "averagePriceViewed", "min": "minPriceViewed", "max": "maxPriceViewed"})
                df = df.merge(view_prices, on="userId", how="left")
            
        # Safe default values
        if "preferredCategory" not in df.columns:
            df["preferredCategory"] = "Unknown"
        else:
            df["preferredCategory"] = df["preferredCategory"].fillna("Unknown")
            
        if "preferredBrand" not in df.columns:
            df["preferredBrand"] = "Unknown"
        else:
            df["preferredBrand"] = df["preferredBrand"].fillna("Unknown")
            
        for col in ["averagePriceViewed", "minPriceViewed", "maxPriceViewed"]:
            if col not in df.columns:
                df[col] = 0.0
            else:
                df[col] = df[col].fillna(0.0)
                
        return df
