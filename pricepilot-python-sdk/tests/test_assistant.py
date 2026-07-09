import pytest
import responses
from pricepilot import PricePilotClient

# Mock base URL pointing to the Spring Boot gateway (the SDK calls Spring Boot for assistant endpoints)
API_BASE_URL = "http://mock-api.local/api/v1"

@pytest.fixture
def client():
    return PricePilotClient(base_url=API_BASE_URL)

@responses.activate
def test_assistant_chat(client):
    """Test SDK client.assistant.chat() API call and structured response parsing."""
    mock_payload = {
        "response": "Here is some shopping advice.",
        "activeProductId": "prod-123",
        "suggestedPrompts": ["Is now a good time to buy?", "Recommend similar items"],
        "products": [
            {
                "id": "prod-123",
                "name": "Gaming Laptop",
                "brand": "Asus",
                "category": "Electronics",
                "price": 75000.0,
                "originalPrice": 80000.0,
                "discount": 6.25,
                "sellersCount": 2,
                "imageUrl": "/laptop.png"
            }
        ],
        "comparisons": None,
        "buyConfidence": {
            "score": 85,
            "reason": "Price is below average."
        },
        "conversationId": "conv-xyz",
        "latency": {"total": 0.45}
    }

    responses.add(
        responses.POST,
        f"{API_BASE_URL}/assistant/chat",
        json=mock_payload,
        status=200
    )

    res = client.assistant.chat(
        message="Show me gaming laptops under 80,000",
        conversation_id="conv-xyz"
    )

    # Validate response structure parsing
    assert res["conversationId"] == "conv-xyz"
    assert res["response"] == "Here is some shopping advice."
    assert res["activeProductId"] == "prod-123"
    assert len(res["suggestedPrompts"]) == 2
    assert len(res["products"]) == 1
    assert res["products"][0]["name"] == "Gaming Laptop"
    assert res["buyConfidence"]["score"] == 85
    assert res["buyConfidence"]["reason"] == "Price is below average."
    
    # Assert correct parameters were sent
    req_body = responses.calls[0].request.body
    import json
    parsed_req = json.loads(req_body)
    assert parsed_req["message"] == "Show me gaming laptops under 80,000"
    assert parsed_req["conversationId"] == "conv-xyz"

@responses.activate
def test_assistant_compare(client):
    """Test SDK client.assistant.compare() API call."""
    mock_payload = {
        "response": "Comparison results...",
        "activeProductId": None,
        "suggestedPrompts": [],
        "products": [],
        "comparisons": {
            "products": [
                {"id": "p1", "name": "Laptop A", "price": 50000.0},
                {"id": "p2", "name": "Laptop B", "price": 60000.0}
            ],
            "summary": "Laptop A is cheaper."
        },
        "buyConfidence": None,
        "conversationId": "conv-xyz"
    }

    responses.add(
        responses.POST,
        f"{API_BASE_URL}/assistant/compare",
        json=mock_payload,
        status=200
    )

    res = client.assistant.compare(
        product_ids=["p1", "p2"],
        conversation_id="conv-xyz"
    )

    assert res["comparisons"]["summary"] == "Laptop A is cheaper."
    assert len(res["comparisons"]["products"]) == 2

@responses.activate
def test_assistant_ask(client):
    """Test SDK client.assistant.ask() API call."""
    mock_payload = {
        "response": "Direct answer to question",
        "activeProductId": None,
        "suggestedPrompts": [],
        "products": [],
        "comparisons": None,
        "buyConfidence": None,
        "conversationId": "single-turn-ask"
    }

    responses.add(
        responses.POST,
        f"{API_BASE_URL}/assistant/ask",
        json=mock_payload,
        status=200
    )

    res = client.assistant.ask(
        question="Is iPhone 16 a good deal?",
        conversation_id="single-turn-ask"
    )

    assert res["response"] == "Direct answer to question"

@responses.activate
def test_assistant_clear_memory(client):
    """Test SDK client.assistant.clear_memory() API call."""
    responses.add(
        responses.POST,
        f"{API_BASE_URL}/assistant/clear_memory",
        json={"status": "success", "message": "Memory cleared"},
        status=200
    )

    res = client.assistant.clear_memory(conversation_id="conv-xyz")
    assert res["status"] == "success"
