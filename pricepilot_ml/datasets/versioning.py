import os
import json
from datetime import datetime
import pandas as pd
from typing import Dict, Any, Optional, Tuple

class DatasetExporter:
    """Manages versioned CSV exports of datasets alongside JSON metadata files in a structured folder tree."""
    def __init__(self, base_dir: str = ".") -> None:
        self.base_dir = os.path.abspath(base_dir)
        self.dirs = {
            "raw": os.path.join(self.base_dir, "datasets", "raw"),
            "processed": os.path.join(self.base_dir, "datasets", "processed"),
            "feature_sets": os.path.join(self.base_dir, "datasets", "feature_sets"),
            "exports": os.path.join(self.base_dir, "datasets", "exports")
        }
        self._ensure_directories()

    def _ensure_directories(self) -> None:
        for path in self.dirs.values():
            os.makedirs(path, exist_ok=True)

    def export(
        self,
        df: pd.DataFrame,
        name: str,
        stage: str,
        version: str,
        schema_version: str,
        generated_by: str,
        future_training_target: Optional[str] = None
    ) -> Tuple[str, str]:
        """Saves a DataFrame as CSV and generates a metadata JSON companion.
        
        Args:
            df: The pandas DataFrame to write out.
            name: Base name of the dataset (e.g. 'products').
            stage: Directory category ('raw', 'processed', 'feature_sets', 'exports').
            version: Version string of the dataset (e.g. '1.0.0').
            schema_version: Schema version of the data structure (e.g. '1.0').
            generated_by: Source client tag.
            future_training_target: Target label column name for future ML training.
            
        Returns:
            Tuple of (csv_filepath, metadata_filepath).
        """
        if stage not in self.dirs:
            raise ValueError(f"Invalid stage '{stage}'. Must be one of {list(self.dirs.keys())}")
            
        folder = self.dirs[stage]
        filename = f"{name}_v{version}"
        csv_path = os.path.join(folder, f"{filename}.csv")
        meta_path = os.path.join(folder, f"{filename}_metadata.json")
        
        # Save DataFrame to CSV
        df.to_csv(csv_path, index=False)
        
        from datetime import timezone
        
        # Build metadata
        metadata = {
            "dataset_version": version,
            "export_timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "record_count": len(df),
            "schema_version": schema_version,
            "generated_by": generated_by,
            "future_training_target": future_training_target or "N/A"
        }
        
        with open(meta_path, "w") as f:
            json.dump(metadata, f, indent=4)
            
        return csv_path, meta_path
