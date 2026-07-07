import pandas as pd
import numpy as np
from datetime import datetime
from typing import List, Dict, Any, Optional, Union

class MissingValueHandler:
    """Handles missing values in numeric and categorical columns."""
    def __init__(self, numeric_strategy: str = "median", categorical_strategy: str = "constant", fill_value: str = "Unknown") -> None:
        self.numeric_strategy = numeric_strategy
        self.categorical_strategy = categorical_strategy
        self.fill_value = fill_value
        self.numeric_imputes: Dict[str, float] = {}
        self.categorical_imputes: Dict[str, Any] = {}

    def fit(self, df: pd.DataFrame, numeric_cols: List[str], categorical_cols: List[str]) -> "MissingValueHandler":
        for col in numeric_cols:
            if col in df.columns:
                if self.numeric_strategy == "median":
                    self.numeric_imputes[col] = float(df[col].median())
                elif self.numeric_strategy == "mean":
                    self.numeric_imputes[col] = float(df[col].mean())
                else:
                    self.numeric_imputes[col] = 0.0
        
        for col in categorical_cols:
            if col in df.columns:
                if self.categorical_strategy == "mode":
                    mode_val = df[col].mode()
                    self.categorical_imputes[col] = mode_val.iloc[0] if not mode_val.empty else self.fill_value
                else:
                    self.categorical_imputes[col] = self.fill_value
        return self

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        df_copy = df.copy()
        for col, val in self.numeric_imputes.items():
            if col in df_copy.columns:
                df_copy[col] = df_copy[col].fillna(val)
        for col, val in self.categorical_imputes.items():
            if col in df_copy.columns:
                df_copy[col] = df_copy[col].fillna(val)
        return df_copy

    def fit_transform(self, df: pd.DataFrame, numeric_cols: List[str], categorical_cols: List[str]) -> pd.DataFrame:
        return self.fit(df, numeric_cols, categorical_cols).transform(df)

class DuplicateRemoval:
    """Utility to drop duplicate rows."""
    @staticmethod
    def remove(df: pd.DataFrame, subset: Optional[List[str]] = None, keep: str = "first") -> pd.DataFrame:
        return df.drop_duplicates(subset=subset, keep=keep)

class OutlierDetector:
    """Detects and clips or drops outliers using the Interquartile Range (IQR) method."""
    def __init__(self, factor: float = 1.5) -> None:
        self.factor = factor
        self.bounds: Dict[str, tuple] = {}

    def fit(self, df: pd.DataFrame, cols: List[str]) -> "OutlierDetector":
        for col in cols:
            if col in df.columns:
                q1 = df[col].quantile(0.25)
                q3 = df[col].quantile(0.75)
                iqr = q3 - q1
                lower = q1 - self.factor * iqr
                upper = q3 + self.factor * iqr
                self.bounds[col] = (lower, upper)
        return self

    def transform(self, df: pd.DataFrame, strategy: str = "cap") -> pd.DataFrame:
        df_copy = df.copy()
        for col, (lower, upper) in self.bounds.items():
            if col in df_copy.columns:
                if strategy == "cap":
                    df_copy[col] = df_copy[col].clip(lower=lower, upper=upper)
                elif strategy == "drop":
                    df_copy = df_copy[(df_copy[col] >= lower) & (df_copy[col] <= upper)]
        return df_copy

    def fit_transform(self, df: pd.DataFrame, cols: List[str], strategy: str = "cap") -> pd.DataFrame:
        return self.fit(df, cols).transform(df, strategy)

class CategoricalEncoder:
    """Performs label encoding on categorical variables, tracking mapped values."""
    def __init__(self) -> None:
        self.mappings: Dict[str, Dict[Any, int]] = {}

    def fit(self, df: pd.DataFrame, cols: List[str]) -> "CategoricalEncoder":
        for col in cols:
            if col in df.columns:
                unique_vals = df[col].dropna().unique()
                self.mappings[col] = {val: i for i, val in enumerate(unique_vals)}
        return self

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        df_copy = df.copy()
        for col, mapping in self.mappings.items():
            if col in df_copy.columns:
                df_copy[col] = df_copy[col].map(mapping).fillna(-1).astype(int)
        return df_copy

    def fit_transform(self, df: pd.DataFrame, cols: List[str]) -> pd.DataFrame:
        return self.fit(df, cols).transform(df)

class PriceNormalizer:
    """Applies log scaling to price features to stabilize variance."""
    @staticmethod
    def log_normalize(df: pd.DataFrame, cols: List[str]) -> pd.DataFrame:
        df_copy = df.copy()
        for col in cols:
            if col in df_copy.columns:
                df_copy[col] = np.log1p(df_copy[col].astype(float))
        return df_copy

class FeatureScaler:
    """Scales numeric features using Standard or MinMax scaling strategies."""
    def __init__(self, strategy: str = "standard") -> None:
        self.strategy = strategy
        self.params: Dict[str, Dict[str, float]] = {}

    def fit(self, df: pd.DataFrame, cols: List[str]) -> "FeatureScaler":
        for col in cols:
            if col in df.columns:
                series = df[col].astype(float)
                if self.strategy == "standard":
                    self.params[col] = {"mean": float(series.mean()), "std": float(series.std() or 1.0)}
                elif self.strategy == "minmax":
                    self.params[col] = {"min": float(series.min()), "max": float(series.max() or 1.0)}
        return self

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        df_copy = df.copy()
        for col, param in self.params.items():
            if col in df_copy.columns:
                if self.strategy == "standard":
                    mean, std = param["mean"], param["std"]
                    df_copy[col] = (df_copy[col] - mean) / std
                elif self.strategy == "minmax":
                    c_min, c_max = param["min"], param["max"]
                    diff = c_max - c_min
                    df_copy[col] = (df_copy[col] - c_min) / (diff if diff != 0 else 1.0)
        return df_copy

    def fit_transform(self, df: pd.DataFrame, cols: List[str]) -> pd.DataFrame:
        return self.fit(df, cols).transform(df)

class TimestampParser:
    """Extracts hour, day of week, and weekend indicator features from datetime string columns."""
    @staticmethod
    def parse(df: pd.DataFrame, col: str) -> pd.DataFrame:
        df_copy = df.copy()
        if col in df_copy.columns:
            parsed = pd.to_datetime(df_copy[col], errors="coerce")
            df_copy[f"{col}_parsed"] = parsed
            df_copy[f"{col}_hour"] = parsed.dt.hour.fillna(-1).astype(int)
            df_copy[f"{col}_dayofweek"] = parsed.dt.dayofweek.fillna(-1).astype(int)
            df_copy[f"{col}_is_weekend"] = parsed.dt.dayofweek.isin([5, 6]).astype(int)
        return df_copy

class NullSafety:
    """Ensures columns exist and fills nulls with safe defaults to prevent run-time errors in models."""
    @staticmethod
    def ensure(df: pd.DataFrame, defaults: Dict[str, Any]) -> pd.DataFrame:
        df_copy = df.copy()
        for col, default in defaults.items():
            if col not in df_copy.columns:
                df_copy[col] = default
            else:
                df_copy[col] = df_copy[col].fillna(default)
        return df_copy
