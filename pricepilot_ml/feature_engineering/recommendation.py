import pandas as pd

class RecommendationFeatureBuilder:
    """Correlates user features with product features to construct recommendation context vectors."""
    def __init__(self) -> None:
        pass

    def build_features(self, df_user_features: pd.DataFrame, df_product_features: pd.DataFrame) -> pd.DataFrame:
        user_df = df_user_features.copy()
        prod_df = df_product_features.copy()

        # If they are already matched/aligned by having a common merge key
        if "userId" in user_df.columns and "userId" in prod_df.columns:
            df = user_df.merge(prod_df, on="userId", how="inner")
        elif "productId" in user_df.columns and "productId" in prod_df.columns:
            df = user_df.merge(prod_df, on="productId", how="inner")
        else:
            # Cross-join fallback to generate all user-product candidate pairs
            user_df = user_df.copy()
            prod_df = prod_df.copy()
            if "id" in prod_df.columns and "productId" not in prod_df.columns:
                prod_df = prod_df.rename(columns={"id": "productId"})
            
            user_df["_key"] = 1
            prod_df["_key"] = 1
            df = user_df.merge(prod_df, on="_key").drop(columns=["_key"])
            
        # 1. Category Matching indicator
        if "preferredCategory" in df.columns and "category" in df.columns:
            df["category_match"] = (df["preferredCategory"] == df["category"]).astype(int)
        else:
            df["category_match"] = 0

        # 2. Brand Matching indicator
        if "preferredBrand" in df.columns and "brand" in df.columns:
            df["brand_match"] = (df["preferredBrand"] == df["brand"]).astype(int)
        else:
            df["brand_match"] = 0

        # 3. Price preference check
        if "currentMinPrice" in df.columns and "minPriceViewed" in df.columns and "maxPriceViewed" in df.columns:
            df["price_in_preference_range"] = (
                (df["currentMinPrice"] >= df["minPriceViewed"]) & 
                (df["currentMinPrice"] <= df["maxPriceViewed"])
            ).astype(int)
        else:
            df["price_in_preference_range"] = 0
            
        return df
