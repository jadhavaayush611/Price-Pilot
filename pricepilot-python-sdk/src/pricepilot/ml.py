import logging
from typing import Any, Dict, List, Optional
from pricepilot.http import HttpClientSession

logger = logging.getLogger("pricepilot.ml")

class MlModule:
    """Module for executing and monitoring machine learning operations in PricePilot."""
    
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def train(self, dataset_version: str = "1.0.0", k: int = 10) -> Dict[str, Any]:
        """Triggers model training and returns the training report."""
        logger.info(f"Triggering training for dataset version: {dataset_version} with k={k}")
        return self._http.request(
            "POST",
            "/api/v1/ml/train",
            json={"datasetVersion": dataset_version, "k": k}
        )

    def evaluate(self, algorithm: str, k: int = 10) -> Dict[str, Any]:
        """Runs evaluation for a specific algorithm."""
        logger.info(f"Triggering evaluation for algorithm: {algorithm} with k={k}")
        return self._http.request(
            "POST",
            "/api/v1/ml/evaluate",
            json={"algorithm": algorithm, "k": k}
        )

    def predict(self, algorithm: str, user_id: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Generates explainable predictions/recommendations using a specific algorithm."""
        logger.info(f"Generating predictions using algorithm: {algorithm} for user: {user_id}")
        return self._http.request(
            "GET",
            f"/api/v1/ml/predict/{algorithm}",
            params={"userId": user_id, "limit": limit}
        )

    def model_metadata(self, algorithm: str) -> Dict[str, Any]:
        """Fetches metadata for a trained model."""
        logger.info(f"Fetching model metadata for algorithm: {algorithm}")
        return self._http.request(
            "GET",
            f"/api/v1/ml/metadata/{algorithm}"
        )

    def training_report(self) -> Dict[str, Any]:
        """Retrieves the latest training and evaluation report."""
        logger.info("Retrieving latest training report")
        return self._http.request(
            "GET",
            "/api/v1/ml/report"
        )
