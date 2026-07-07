import pandas as pd
import json
from typing import Optional

class InteractionFeatureBuilder:
    """Parses interaction events into user session attributes, temporal indicators, and keyword extractions."""
    def __init__(self) -> None:
        pass

    def build_features(self, df_interactions: pd.DataFrame, df_products: Optional[pd.DataFrame] = None) -> pd.DataFrame:
        df = df_interactions.copy()
        
        # 1. Temporal features
        if "createdAt" in df.columns:
            parsed = pd.to_datetime(df["createdAt"], errors="coerce")
            df["hour"] = parsed.dt.hour.fillna(-1).astype(int)
            df["dayofweek"] = parsed.dt.dayofweek.fillna(-1).astype(int)
            df["is_weekend"] = parsed.dt.dayofweek.isin([5, 6]).astype(int)
            
        # 2. Session calculations (events within a 30-minute window)
        if "userId" in df.columns and "createdAt" in df.columns:
            df = df.sort_values(by=["userId", "createdAt"])
            df["time_diff"] = df.groupby("userId")["createdAt"].transform(
                lambda x: pd.to_datetime(x).diff().dt.total_seconds().fillna(0.0)
            )
            df["new_session"] = (df["time_diff"] > 1800).astype(int)
            df["session_id"] = df.groupby("userId")["new_session"].cumsum()
            df["session_length"] = df.groupby(["userId", "session_id"]).cumcount() + 1
        else:
            df["session_length"] = 1
            
        # 3. Extract search keywords
        if "metadataJson" in df.columns:
            def extract_keyword(x):
                try:
                    if pd.isna(x) or not x:
                        return ""
                    meta = json.loads(str(x))
                    return meta.get("keyword", "")
                except Exception:
                    return ""
            df["search_keyword"] = df["metadataJson"].apply(extract_keyword)
        else:
            df["search_keyword"] = ""

        # 4. Integrate product categories/brands
        if df_products is not None and not df_products.empty:
            prod_subset = df_products[["id", "category", "brand"]].rename(
                columns={"id": "productId", "category": "productCategory", "brand": "productBrand"}
            )
            df = df.merge(prod_subset, on="productId", how="left")
            df["productCategory"] = df["productCategory"].fillna("Unknown")
            df["productBrand"] = df["productBrand"].fillna("Unknown")
        else:
            df["productCategory"] = "Unknown"
            df["productBrand"] = "Unknown"
            
        return df
