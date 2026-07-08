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
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "details" in data

def test_metrics_endpoint():
    """Asserts metrics endpoint works without authentication."""
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
