import time
from fastapi import APIRouter, Depends, HTTPException, Security, status
from fastapi.security.api_key import APIKeyHeader
from typing import Dict, Any, List
from app.config.settings import settings
from app.schemas.recommendation import (
    PredictRequest, PredictResponse, SimilarRequest, SimilarResponse
)
from app.services.prediction import prediction_service
from app.loaders.model_registry import model_registry
from app.utils.logger import log_structured
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from fastapi.responses import Response
import logging

router = APIRouter()

# Security API key header definition
API_KEY_HEADER = APIKeyHeader(name="X-API-Key", auto_error=False)

# Prometheus metrics setup
REQUEST_COUNT = Counter(
    "pricepilot_ai_requests_total",
    "Total number of requests to PricePilot AI service",
    ["endpoint", "algorithm", "status"]
)
INFERENCE_LATENCY = Histogram(
    "pricepilot_ai_inference_duration_seconds",
    "Inference latency in seconds",
    ["endpoint", "algorithm"]
)

import os
from fastapi import Header

def verify_api_key(api_key: str = Security(API_KEY_HEADER)):
    """Validates that the provided X-API-Key matches our configured secret."""
    if not api_key:
        log_structured(logging.WARNING, "api_key_missing")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="API Key missing in X-API-Key header"
        )
    if api_key != settings.api_key:
        log_structured(logging.WARNING, "AUDIT: API_KEY_FAILURE", {"reason": "invalid_api_key"})
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid API Key"
        )
    return api_key

def verify_reload_token(
    x_model_reload_token: Optional[str] = Header(None, alias="X-Model-Reload-Token"),
    api_key: str = Depends(verify_api_key)
):
    """Additional verification for model reloads beyond API key validation."""
    reload_token = os.getenv("PRICEPILOT_MODEL_RELOAD_TOKEN")
    if reload_token:
        if not x_model_reload_token or x_model_reload_token != reload_token:
            log_structured(logging.WARNING, "AUDIT: RELOAD_TOKEN_FAILURE", {"reason": "invalid_or_missing_reload_token"})
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Invalid or missing Model Reload Token"
            )
    return api_key

@router.post(
    "/recommendations/predict",
    response_model=PredictResponse,
    dependencies=[Depends(verify_api_key)]
)
def predict(request: PredictRequest):
    """Scores candidate products for a user using the selected recommender model."""
    start_time = time.time()
    algo = request.algorithm
    log_structured(logging.INFO, "predict_request_received", {
        "userId": request.userId,
        "algorithm": algo,
        "candidate_count": len(request.candidates)
    })

    try:
        response = prediction_service.predict_recommendations(request)
        latency = time.time() - start_time
        
        # Track metrics
        REQUEST_COUNT.labels(endpoint="predict", algorithm=algo, status="success").inc()
        INFERENCE_LATENCY.labels(endpoint="predict", algorithm=algo).observe(latency)
        
        log_structured(logging.INFO, "predict_request_success", {
            "userId": request.userId,
            "algorithm": response.algorithm,
            "latency_seconds": latency
        })
        return response
    except Exception as e:
        latency = time.time() - start_time
        REQUEST_COUNT.labels(endpoint="predict", algorithm=algo, status="error").inc()
        log_structured(logging.ERROR, "predict_request_failed", {
            "userId": request.userId,
            "algorithm": algo,
            "error": str(e),
            "latency_seconds": latency
        })
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Prediction failed: {str(e)}"
        )

@router.post(
    "/recommendations/similar",
    response_model=SimilarResponse,
    dependencies=[Depends(verify_api_key)]
)
def similar(request: SimilarRequest):
    """Generates similar product recommendations for a target product."""
    start_time = time.time()
    log_structured(logging.INFO, "similar_request_received", {
        "targetProductId": request.targetProductId,
        "candidate_count": len(request.candidates)
    })

    try:
        response = prediction_service.predict_similar_products(request)
        latency = time.time() - start_time
        
        REQUEST_COUNT.labels(endpoint="similar", algorithm="metadata", status="success").inc()
        INFERENCE_LATENCY.labels(endpoint="similar", algorithm="metadata").observe(latency)
        
        log_structured(logging.INFO, "similar_request_success", {
            "targetProductId": request.targetProductId,
            "latency_seconds": latency
        })
        return response
    except Exception as e:
        latency = time.time() - start_time
        REQUEST_COUNT.labels(endpoint="similar", algorithm="metadata", status="error").inc()
        log_structured(logging.ERROR, "similar_request_failed", {
            "targetProductId": request.targetProductId,
            "error": str(e)
        })
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Similarity matching failed: {str(e)}"
        )

@router.get(
    "/models",
    dependencies=[Depends(verify_api_key)]
)
def get_models():
    """Lists metadata for all loaded models in the registry."""
    return model_registry.get_all_metadata()

@router.get(
    "/models/{algorithm}",
    dependencies=[Depends(verify_api_key)]
)
def get_model(algorithm: str):
    """Retrieves metadata for a specific algorithm."""
    metadata = model_registry.get_metadata(algorithm)
    if not metadata:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Model metadata for '{algorithm}' not found"
        )
    return metadata

@router.post(
    "/models/reload",
    dependencies=[Depends(verify_reload_token)]
)
def reload_models():
    """Forces the model registry to reload pickle files from disk."""
    success = model_registry.reload()
    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to reload models. Check logs for details."
        )
    return {"status": "success", "message": "Models reloaded successfully"}

@router.get("/health")
def health_check(response: Response):
    """Performs self-checks for liveness and readiness."""
    details = {
        "models_loaded": model_registry.is_loaded,
        "loaded_algorithms": list(model_registry._models.keys())
    }
    
    if not model_registry.is_loaded:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {
            "status": "DOWN",
            "details": details
        }
        
    return {
        "status": "UP",
        "details": details
    }

@router.get("/health/liveness")
def liveness_check():
    """Liveness check to verify the API server is up and responding."""
    return {"status": "UP"}

@router.get("/health/readiness")
def readiness_check(response: Response):
    """Readiness check to verify that models are loaded and ready to serve."""
    details = {
        "models_loaded": model_registry.is_loaded,
        "loaded_algorithms": list(model_registry._models.keys())
    }
    if not model_registry.is_loaded:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {
            "status": "DOWN",
            "details": details
        }
    return {
        "status": "UP",
        "details": details
    }

@router.get("/metrics", dependencies=[Depends(verify_api_key)])
def get_metrics():
    """Exposes Prometheus compatible scrape metrics."""
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)

