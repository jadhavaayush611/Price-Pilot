import os
import sys
import pickle
import json
import threading
from typing import Dict, Any, Optional
from app.config.settings import settings
from app.utils.logger import log_structured, logger
import logging

# Ensure workspace root is in sys.path before pickle imports models
# Pickle needs to resolve classes like pricepilot_ml.recommendation.models.popularity.PopularityRecommender
workspace_root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
if workspace_root not in sys.path:
    sys.path.insert(0, workspace_root)

class ModelRegistry:
    def __init__(self):
        self._models: Dict[str, Any] = {}
        self._metadata: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.Lock()
        self.is_loaded = False

    def load_all_models(self) -> bool:
        """Loads all recommender models and metadata from model directory.
        Returns True if successful, False otherwise.
        """
        with self._lock:
            import time
            start_time = time.time()
            temp_models = {}
            temp_metadata = {}
            model_dir = settings.model_dir
            log_structured(logging.INFO, "loading_models_start", {"model_dir": model_dir})

            algorithms = ["popularity", "content", "collaborative", "hybrid"]
            
            if not os.path.exists(model_dir):
                log_structured(logging.ERROR, "model_dir_not_found", {"model_dir": model_dir})
                # Fallback check
                fallback_dir = os.path.join(workspace_root, "models", "recommendation")
                if os.path.exists(fallback_dir):
                    model_dir = fallback_dir
                    log_structured(logging.INFO, "using_fallback_model_dir", {"model_dir": model_dir})
                else:
                    logger.error(f"Model directory {model_dir} does not exist.")
                    return False

            success_count = 0
            for algo in algorithms:
                model_file = f"{algo}_model.pkl"
                meta_file = f"{algo}_metadata.json"

                model_path = os.path.join(model_dir, model_file)
                meta_path = os.path.join(model_dir, meta_file)

                if os.path.exists(model_path):
                    try:
                        with open(model_path, "rb") as f:
                            temp_models[algo] = pickle.load(f)
                        
                        # Try to load metadata
                        if os.path.exists(meta_path):
                            with open(meta_path, "r") as f:
                                temp_metadata[algo] = json.load(f)
                        else:
                            temp_metadata[algo] = {
                                "algorithm": algo.capitalize(),
                                "datasetVersion": "Unknown",
                                "featureVersion": "Unknown",
                                "trainedAt": "Unknown",
                                "modelFile": model_file,
                                "configuration": {}
                            }
                        success_count += 1
                        log_structured(logging.INFO, "model_loaded_successfully", {"algorithm": algo})
                    except Exception as e:
                        # Clean error message to avoid path leak
                        err_msg = str(e)
                        if model_path in err_msg:
                            err_msg = err_msg.replace(model_path, model_file)
                        log_structured(logging.ERROR, "model_load_failed", {"algorithm": algo, "error": f"{type(e).__name__}: {err_msg}"})
                else:
                    log_structured(logging.WARNING, "model_file_missing", {"algorithm": algo, "path": model_path})

            # Check if training report exists
            report_path = os.path.join(model_dir, "training_report.json")
            if os.path.exists(report_path):
                try:
                    with open(report_path, "r") as f:
                        temp_metadata["training_report"] = json.load(f)
                except Exception as e:
                    log_structured(logging.WARNING, "training_report_load_failed", {"error": str(e)})

            load_duration = time.time() - start_time
            if success_count > 0:
                self._models = temp_models
                self._metadata = temp_metadata
                self.is_loaded = True
                log_structured(logging.INFO, "all_models_loaded", {"loaded_count": success_count, "load_duration_seconds": load_duration})
                return True
            else:
                log_structured(logging.ERROR, "no_models_loaded")
                return False

    def reload(self) -> bool:
        """Atomic hot reload wrapper."""
        log_structured(logging.INFO, "model_reload_requested")
        return self.load_all_models()

    def get_model(self, algo: str) -> Optional[Any]:
        """Returns loaded model object."""
        return self._models.get(algo.lower().strip())

    def get_metadata(self, algo: str) -> Optional[Dict[str, Any]]:
        """Returns model metadata dict."""
        return self._metadata.get(algo.lower().strip())

    def get_all_metadata(self) -> Dict[str, Any]:
        """Returns metadata for all algorithms."""
        return self._metadata

model_registry = ModelRegistry()
