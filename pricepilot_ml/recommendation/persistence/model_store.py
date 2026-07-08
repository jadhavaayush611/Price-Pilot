import os
import json
import pickle
from datetime import datetime, timezone
from typing import Dict, Any, Optional

class ModelStore:
    """Handles persistence of trained recommendation models and their associated metadata."""
    
    def __init__(self, base_dir: str = ".") -> None:
        self.base_dir = os.path.abspath(base_dir)
        self.model_dir = os.path.join(self.base_dir, "models", "recommendation")
        os.makedirs(self.model_dir, exist_ok=True)

    def persist(
        self,
        model: Any,
        algorithm: str,
        dataset_version: str,
        feature_version: str,
        evaluation_metrics: Dict[str, float],
        configuration: Dict[str, Any],
        model_filename: Optional[str] = None
    ) -> str:
        """Saves model file and metadata JSON file.
        
        Returns path to the metadata file.
        """
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        safe_algo = algorithm.lower().replace(" ", "_")
        
        if not model_filename:
            model_filename = f"{safe_algo}_model.pkl"
            
        model_path = os.path.join(self.model_dir, model_filename)
        meta_path = os.path.join(self.model_dir, f"{safe_algo}_metadata.json")
        
        # Save model object
        with open(model_path, "wb") as f:
            pickle.dump(model, f)
            
        # Compile metadata
        metadata = {
            "algorithm": algorithm,
            "datasetVersion": dataset_version,
            "featureVersion": feature_version,
            "trainedAt": timestamp,
            "modelFile": model_filename,
            "configuration": configuration,
            **evaluation_metrics
        }
        
        with open(meta_path, "w") as f:
            json.dump(metadata, f, indent=4)
            
        return meta_path

    def load_model(self, algorithm: str) -> Any:
        """Loads a model for the given algorithm."""
        safe_algo = algorithm.lower().replace(" ", "_")
        model_filename = f"{safe_algo}_model.pkl"
        model_path = os.path.join(self.model_dir, model_filename)
        
        if not os.path.exists(model_path):
            # Fallback check inside the parent directory / pricepilot_ml or workspace
            model_path_fallback = os.path.join(self.base_dir, "pricepilot_ml", "models", "recommendation", model_filename)
            if os.path.exists(model_path_fallback):
                model_path = model_path_fallback
            else:
                raise FileNotFoundError(f"No persisted model found for algorithm: {algorithm} at {model_path}")
            
        with open(model_path, "rb") as f:
            return pickle.load(f)

    def load_metadata(self, algorithm: str) -> Dict[str, Any]:
        """Loads metadata for the given algorithm."""
        safe_algo = algorithm.lower().replace(" ", "_")
        meta_path = os.path.join(self.model_dir, f"{safe_algo}_metadata.json")
        
        if not os.path.exists(meta_path):
            meta_path_fallback = os.path.join(self.base_dir, "pricepilot_ml", "models", "recommendation", f"{safe_algo}_metadata.json")
            if os.path.exists(meta_path_fallback):
                meta_path = meta_path_fallback
            else:
                raise FileNotFoundError(f"No metadata found for algorithm: {algorithm} at {meta_path}")
            
        with open(meta_path, "r") as f:
            return json.load(f)
