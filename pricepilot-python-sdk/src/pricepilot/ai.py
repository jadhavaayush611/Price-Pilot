import logging
from typing import Any, Dict, List, Optional
from pricepilot.http import HttpClientSession

logger = logging.getLogger("pricepilot.ai")

class AiModule:
    """Module for communicating with the FastAPI AI Microservice."""

    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def predict(
        self,
        user_id: str,
        candidates: List[Dict[str, Any]],
        algorithm: str = "Hybrid",
        limit: int = 10,
        user_profile: Optional[Dict[str, Any]] = None,
        interactions: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """Generates recommendations by scoring candidate products."""
        logger.info(f"Generating AI predictions using algorithm: {algorithm} for user: {user_id}")
        payload = {
            "userId": user_id,
            "algorithm": algorithm,
            "limit": limit,
            "candidates": candidates
        }
        if user_profile:
            payload["userProfile"] = user_profile
        if interactions:
            payload["interactions"] = interactions

        return self._http.request(
            "POST",
            "/recommendations/predict",
            json=payload,
            is_ai=True
        )

    def similar(
        self,
        target_product_id: str,
        target_product: Dict[str, Any],
        candidates: List[Dict[str, Any]],
        limit: int = 10
    ) -> Dict[str, Any]:
        """Calculates similar products for a given target product."""
        logger.info(f"Generating AI similarities for product: {target_product_id}")
        payload = {
            "targetProductId": target_product_id,
            "targetProduct": target_product,
            "candidates": candidates,
            "limit": limit
        }
        return self._http.request(
            "POST",
            "/recommendations/similar",
            json=payload,
            is_ai=True
        )

    def models(self, algorithm: Optional[str] = None) -> Any:
        """Retrieves metadata for loaded models or a specific algorithm model."""
        if algorithm:
            logger.info(f"Retrieving metadata for AI model algorithm: {algorithm}")
            return self._http.request("GET", f"/models/{algorithm}", is_ai=True)
        logger.info("Retrieving metadata for all loaded AI models")
        return self._http.request("GET", "/models", is_ai=True)

    def reload(self) -> Dict[str, Any]:
        """Forces the FastAPI model registry to reload model pickles."""
        logger.info("Requesting reload of all AI models")
        return self._http.request("POST", "/models/reload", is_ai=True)

    def health(self) -> Dict[str, Any]:
        """Checks the health and readiness of the FastAPI service."""
        logger.debug("Checking FastAPI AI service health")
        return self._http.request("GET", "/health", is_ai=True)

    def metrics(self) -> str:
        """Exposes raw scrape metrics from the FastAPI service."""
        logger.debug("Retrieving FastAPI metrics")
        return self._http.request("GET", "/metrics", is_ai=True)
