import pytest
import responses
from pricepilot import PricePilotClient

# Mock base URLs
API_BASE_URL = "http://mock-api.local/api/v1"
AI_BASE_URL = "http://mock-ai.local"

@pytest.fixture
def client():
    return PricePilotClient(base_url=API_BASE_URL, config=None)

@pytest.fixture
def client_with_ai():
    client = PricePilotClient(base_url=API_BASE_URL)
    client.config.ai_base_url = AI_BASE_URL
    client.config.ai_api_key = "test-api-key"
    # Re-instantiate session to read updated config
    client._http.config = client.config
    return client

@responses.activate
def test_ai_predict(client_with_ai):
    responses.add(
        responses.POST,
        f"{AI_BASE_URL}/recommendations/predict",
        json={
            "algorithm": "Hybrid",
            "score": 0.95,
            "recommendations": [
                {
                    "productId": "prod_1",
                    "score": 0.95,
                    "reasons": ["Popular in your brand preference"]
                }
            ]
        },
        status=200
    )

    candidates = [
        {"productId": "prod_1", "category": "Shoes", "brand": "Adidas", "currentMinPrice": 100.0}
    ]
    user_profile = {
        "preferredCategories": {"Shoes": 1.0},
        "preferredBrands": {"Adidas": 1.0}
    }
    interactions = [
        {"productId": "prod_1", "interactionType": "PRODUCT_VIEW"}
    ]

    res = client_with_ai.ai.predict(
        user_id="user_123",
        candidates=candidates,
        algorithm="Hybrid",
        limit=5,
        user_profile=user_profile,
        interactions=interactions
    )

    assert res["algorithm"] == "Hybrid"
    assert res["score"] == 0.95
    assert len(res["recommendations"]) == 1
    assert res["recommendations"][0]["productId"] == "prod_1"
    
    # Assert headers contain the X-API-Key
    assert responses.calls[0].request.headers["X-API-Key"] == "test-api-key"

@responses.activate
def test_ai_similar(client_with_ai):
    responses.add(
        responses.POST,
        f"{AI_BASE_URL}/recommendations/similar",
        json={
            "targetProductId": "prod_target",
            "similarProducts": [
                {
                    "productId": "prod_similar",
                    "score": 0.85,
                    "reasons": ["Same brand", "Close price"]
                }
            ]
        },
        status=200
    )

    target_product = {"productId": "prod_target", "category": "Bikes", "brand": "Trek"}
    candidates = [
        {"productId": "prod_similar", "category": "Bikes", "brand": "Trek", "currentMinPrice": 500.0}
    ]

    res = client_with_ai.ai.similar(
        target_product_id="prod_target",
        target_product=target_product,
        candidates=candidates,
        limit=3
    )

    assert res["targetProductId"] == "prod_target"
    assert len(res["similarProducts"]) == 1
    assert res["similarProducts"][0]["productId"] == "prod_similar"

@responses.activate
def test_ai_models(client_with_ai):
    # Test all models metadata
    responses.add(
        responses.GET,
        f"{AI_BASE_URL}/models",
        json={
            "popularity": {"algorithm": "Popularity", "datasetVersion": "1.0.0"},
            "hybrid": {"algorithm": "Hybrid", "datasetVersion": "1.0.0"}
        },
        status=200
    )
    
    models = client_with_ai.ai.models()
    assert "popularity" in models
    assert "hybrid" in models

    # Test single algorithm metadata
    responses.add(
        responses.GET,
        f"{AI_BASE_URL}/models/hybrid",
        json={"algorithm": "Hybrid", "datasetVersion": "1.0.0"},
        status=200
    )
    
    model = client_with_ai.ai.models(algorithm="hybrid")
    assert model["algorithm"] == "Hybrid"

@responses.activate
def test_ai_reload(client_with_ai):
    responses.add(
        responses.POST,
        f"{AI_BASE_URL}/models/reload",
        json={"status": "success", "message": "Models reloaded"},
        status=200
    )
    
    res = client_with_ai.ai.reload()
    assert res["status"] == "success"

@responses.activate
def test_ai_health(client_with_ai):
    responses.add(
        responses.GET,
        f"{AI_BASE_URL}/health",
        json={"status": "UP", "details": {"models_loaded": True}},
        status=200
    )
    
    res = client_with_ai.ai.health()
    assert res["status"] == "UP"

@responses.activate
def test_ai_metrics(client_with_ai):
    responses.add(
        responses.GET,
        f"{AI_BASE_URL}/metrics",
        body="# HELP pricepilot_ai_requests_total\n# TYPE pricepilot_ai_requests_total counter",
        status=200
    )
    
    metrics = client_with_ai.ai.metrics()
    assert "pricepilot_ai_requests_total" in metrics
