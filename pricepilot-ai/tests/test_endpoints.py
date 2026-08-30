import sys
import os
from fastapi.testclient import TestClient
import pytest

# Ensure app is importable
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.main import app
from app.config.settings import settings

client = TestClient(app)

API_KEY_HEADER = {"X-API-Key": settings.api_key}

def test_health_endpoint():
    """Asserts health check works without authentication."""
    from app.loaders.model_registry import model_registry
    model_registry.is_loaded = True
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data

def test_metrics_endpoint():
    """Asserts metrics endpoint works for Prometheus scrapers."""
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "pricepilot_ai" in response.text or "# HELP" in response.text

def test_authentication_required():
    """Asserts protected routes return 401/403 when API key is missing or wrong."""
    routes = [
        ("/models", "get", None),
        ("/models/popularity", "get", None),
        ("/models/reload", "post", None),
        ("/recommendations/predict", "post", {}),
        ("/recommendations/similar", "post", {})
    ]
    for url, method, body in routes:
        # Missing key
        if method == "get":
            res = client.get(url)
        else:
            res = client.post(url, json=body)
        assert res.status_code == 401

        # Invalid key
        headers = {"X-API-Key": "wrong-key"}
        if method == "get":
            res = client.get(url, headers=headers)
        else:
            res = client.post(url, json=body, headers=headers)
        assert res.status_code == 403

def test_predict_endpoint_validation():
    """Asserts predict API validates input using Pydantic schemas."""
    # Send empty body
    res = client.post("/recommendations/predict", json={}, headers=API_KEY_HEADER)
    assert res.status_code == 422

    # Send missing fields
    payload = {
        "userId": "user_1",
        "algorithm": "Hybrid"
    }
    res = client.post("/recommendations/predict", json=payload, headers=API_KEY_HEADER)
    assert res.status_code == 422

def test_predict_endpoint_success():
    """Asserts a valid prediction request returns scoring recommendations and reasons."""
    payload = {
        "userId": "user_1",
        "algorithm": "Popularity",
        "limit": 2,
        "candidates": [
            {
                "productId": "prod_1",
                "category": "Electronics",
                "brand": "BrandA",
                "currentMinPrice": 150.0,
                "viewCount": 200,
                "saveCount": 20,
                "watchlistCount": 10,
                "trendingScore": 85.5,
                "discountPercentage": 10.0
            },
            {
                "productId": "prod_2",
                "category": "Electronics",
                "brand": "BrandB",
                "currentMinPrice": 99.0,
                "viewCount": 50,
                "saveCount": 5,
                "watchlistCount": 2,
                "trendingScore": 15.0,
                "discountPercentage": 20.0
            }
        ]
    }
    res = client.post("/recommendations/predict", json=payload, headers=API_KEY_HEADER)
    assert res.status_code == 200
    data = res.json()
    assert data["algorithm"] in ["Popularity", "Popularity (Fallback)"]
    assert len(data["recommendations"]) == 2
    assert data["recommendations"][0]["productId"] == "prod_1"  # prod_1 has higher view/save counts
    assert len(data["recommendations"][0]["reasons"]) > 0

def test_similar_endpoint_success():
    """Asserts a valid similarity request returns similar products ranked by attribute scoring."""
    payload = {
        "targetProductId": "prod_1",
        "targetProduct": {
            "productId": "prod_1",
            "category": "Electronics",
            "brand": "BrandA",
            "currentMinPrice": 100.0
        },
        "candidates": [
            {
                "productId": "prod_2",
                "category": "Electronics",  # Category match (50 pts)
                "brand": "BrandA",          # Brand match (30 pts)
                "currentMinPrice": 95.0      # Close price (20 pts)
            },
            {
                "productId": "prod_3",
                "category": "Clothing",     # No cat match
                "brand": "BrandB",          # No brand match
                "currentMinPrice": 100.0     # Equal price (20 pts)
            }
        ]
    }
    res = client.post("/recommendations/similar", json=payload, headers=API_KEY_HEADER)
    assert res.status_code == 200
    data = res.json()
    assert data["targetProductId"] == "prod_1"
    assert len(data["similarProducts"]) == 2
    # prod_2 should have higher similarity score than prod_3
    assert data["similarProducts"][0]["productId"] == "prod_2"
    assert data["similarProducts"][0]["score"] > data["similarProducts"][1]["score"]

def test_explain_endpoint_success():
    """Asserts that /recommendations/explain generates grounded explanations with supporting factors and trade-offs."""
    payload = {
        "recommendedProductId": "prod_1",
        "recommendedProductName": "iPhone 15 Pro",
        "recommendationType": "BEST_OVERALL",
        "score": 91.5,
        "confidence": 0.88,
        "evidence": [
            {
                "productId": "prod_1",
                "productName": "iPhone 15 Pro",
                "type": "LOWEST_PRICE",
                "description": "Lowest current price among compared products at $999.00",
                "metricName": "PRICE",
                "metricValue": 999.0,
                "comparisonValue": 999.0,
                "isPositive": True,
                "importance": 0.95
            },
            {
                "productId": "prod_1",
                "productName": "iPhone 15 Pro",
                "type": "HIGHEST_RATING",
                "description": "Highest customer satisfaction rating of 4.8/5.0",
                "metricName": "RATING",
                "metricValue": 4.8,
                "comparisonValue": 4.8,
                "isPositive": True,
                "importance": 0.90
            }
        ],
        "tradeOffEvidence": [
            {
                "productId": "prod_2",
                "productName": "Galaxy S24 Ultra",
                "type": "BETTER_SPECIFICATION",
                "description": "Galaxy S24 Ultra has a higher rating (4.9 vs 4.8), but costs $200.00 more",
                "metricName": "RATING",
                "metricValue": 4.9,
                "comparisonValue": 4.8,
                "isPositive": False,
                "importance": 0.80
            }
        ]
    }
    res = client.post("/recommendations/explain", json=payload, headers=API_KEY_HEADER)
    assert res.status_code == 200
    data = res.json()
    assert "explanation" in data
    assert "iPhone 15 Pro" in data["explanation"]
    assert len(data["supportingFactors"]) == 2
    assert "Lowest current price" in data["supportingFactors"][0]
    assert len(data["tradeOffs"]) == 1
    assert "Galaxy S24 Ultra" in data["tradeOffs"][0]
    assert data["model"] == "PricePilot-Explainability-v1"

def test_explain_endpoint_schema_validation():
    """Asserts that invalid/malformed payloads return HTTP 422 Unprocessable Entity."""
    # Missing required fields
    res = client.post("/recommendations/explain", json={}, headers=API_KEY_HEADER)
    assert res.status_code == 422

    # Malformed score/confidence types
    bad_payload = {
        "recommendedProductId": "prod_1",
        "recommendedProductName": "Phone",
        "score": "not-a-number",
        "confidence": "invalid"
    }
    res2 = client.post("/recommendations/explain", json=bad_payload, headers=API_KEY_HEADER)
    assert res2.status_code == 422

def test_explain_endpoint_authentication():
    """Asserts authentication is enforced on /recommendations/explain."""
    payload = {
        "recommendedProductId": "prod_1",
        "recommendedProductName": "Phone",
        "score": 90.0,
        "confidence": 0.85
    }
    # No header
    res_no_auth = client.post("/recommendations/explain", json=payload)
    assert res_no_auth.status_code == 401

    # Invalid header
    res_bad_auth = client.post("/recommendations/explain", json=payload, headers={"X-API-Key": "wrong"})
    assert res_bad_auth.status_code == 403
