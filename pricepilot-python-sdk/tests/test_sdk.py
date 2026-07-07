import pytest
import responses
from decimal import Decimal
from datetime import datetime, timezone
from pricepilot import (
    PricePilotClient,
    PricePilotConfig,
    ValidationError,
    AuthenticationError,
    NotFoundError,
    ApiError
)
from pricepilot.models import (
    UserResponse,
    AuthResponse,
    SellerResponse,
    ProductResponse,
    ProductPriceResponse,
    ProductSearchResult,
    PageResponse,
    DashboardData,
    ProductAnalyticsResponse
)

# Mock config
BASE_URL = "http://mock-api.local/api/v1"

@pytest.fixture
def client():
    return PricePilotClient(base_url=BASE_URL)

def test_client_initialization():
    client_default = PricePilotClient()
    assert client_default.config.base_url == "http://localhost:8080/api/v1"
    assert client_default.config.timeout == 10.0
    
    config = PricePilotConfig(base_url="http://custom-host/api/v1", timeout=5.0)
    client_custom = PricePilotClient(config=config)
    assert client_custom.config.base_url == "http://custom-host/api/v1"
    assert client_custom.config.timeout == 5.0

    client_params = PricePilotClient(base_url="http://params-host/api/v1", timeout=2.5)
    assert client_params.config.base_url == "http://params-host/api/v1"
    assert client_params.config.timeout == 2.5

def test_model_serialization_deserialization():
    # Test date parsing and decimal parsing helper
    seller_dict = {
        "id": "seller-id-123",
        "name": "Amazon",
        "websiteUrl": "https://amazon.com",
        "logoUrl": "https://amazon.com/logo.png",
        "createdAt": "2026-06-21T22:30:20Z",
        "updatedAt": "2026-06-21T22:30:20Z"
    }
    seller = SellerResponse.from_dict(seller_dict)
    assert seller.id == "seller-id-123"
    assert seller.name == "Amazon"
    assert seller.created_at.year == 2026
    assert seller.created_at.tzinfo == timezone.utc

    product_dict = {
        "id": "prod-123",
        "name": "Sony Headphones",
        "category": "Electronics",
        "brand": "Sony",
        "description": "Noise-cancelling headphones",
        "imageUrl": "https://example.com/sony.jpg",
        "archived": False,
        "createdAt": "2026-06-21T22:30:20Z",
        "updatedAt": "2026-06-21T22:30:20Z",
        "prices": [
            {
                "id": "price-123",
                "currentPrice": 299.99,
                "originalPrice": 399.99,
                "discountPercentage": 25.0,
                "productUrl": "https://amazon.com/sony",
                "lastUpdated": "2026-06-21T22:30:20Z",
                "seller": seller_dict
            }
        ]
    }
    product = ProductResponse.from_dict(product_dict)
    assert product.id == "prod-123"
    assert len(product.prices) == 1
    assert product.prices[0].current_price == Decimal("299.99")
    assert product.prices[0].seller.name == "Amazon"

@responses.activate
def test_auth_flow_mocked(client):
    # Mock register response
    responses.add(
        responses.POST,
        f"{BASE_URL}/auth/register",
        json={
            "token": "register-mock-jwt-token",
            "user": {
                "id": "user-id-1",
                "email": "test@example.com",
                "firstName": "John",
                "lastName": "Doe",
                "role": "USER",
                "enabled": True
            }
        },
        status=201
    )
    
    auth_response = client.auth.register(
        email="test@example.com",
        password="password123",
        first_name="John",
        last_name="Doe"
    )
    assert auth_response.token == "register-mock-jwt-token"
    assert auth_response.user.email == "test@example.com"
    assert client._http.session.headers["Authorization"] == "Bearer register-mock-jwt-token"

    # Mock login response
    responses.add(
        responses.POST,
        f"{BASE_URL}/auth/login",
        json={
            "token": "login-mock-jwt-token",
            "user": {
                "id": "user-id-1",
                "email": "test@example.com",
                "firstName": "John",
                "lastName": "Doe",
                "role": "USER",
                "enabled": True
            }
        },
        status=200
    )
    
    auth_response_login = client.auth.login(email="test@example.com", password="password123")
    assert auth_response_login.token == "login-mock-jwt-token"
    assert client._http.session.headers["Authorization"] == "Bearer login-mock-jwt-token"

    # Test logout
    client.auth.logout()
    assert "Authorization" not in client._http.session.headers

@responses.activate
def test_exception_mapping(client):
    # 400 Validation Error
    responses.add(
        responses.POST,
        f"{BASE_URL}/products",
        json={
            "timestamp": "2026-06-21T22:36:00Z",
            "status": 400,
            "error": "Bad Request",
            "message": "Validation failed",
            "validationErrors": [
                {"field": "name", "message": "must not be blank"}
            ]
        },
        status=400
    )
    with pytest.raises(ValidationError) as exc_info:
        client.products.create_product(name="", brand="Apple", category="Phones")
    assert exc_info.value.validation_errors[0]["field"] == "name"
    assert "Validation failed" in str(exc_info.value)

    # 401 Authentication Error
    responses.add(
        responses.GET,
        f"{BASE_URL}/dashboard",
        json={"message": "Full authentication is required to access this resource"},
        status=401
    )
    with pytest.raises(AuthenticationError) as exc_info:
        client.dashboard.get_dashboard_data()
    assert "authentication" in str(exc_info.value).lower()

    # 404 Not Found Error
    responses.add(
        responses.GET,
        f"{BASE_URL}/products/missing-id",
        json={"message": "Product not found"},
        status=404
    )
    with pytest.raises(NotFoundError):
        client.products.get_product("missing-id")

    # 500 Unexpected Api Error
    responses.add(
        responses.GET,
        f"{BASE_URL}/products/popular",
        body="Internal Server Error",
        status=500
    )
    with pytest.raises(ApiError) as exc_info:
        client.products.get_popular_products()
    assert exc_info.value.status_code == 500
    assert "Internal Server Error" in exc_info.value.response_body

@responses.activate
def test_products_search_handling(client):
    responses.add(
        responses.GET,
        f"{BASE_URL}/search",
        json={
            "content": [
                {
                    "id": "prod-uuid",
                    "name": "Sony Headphones",
                    "brand": "Sony",
                    "category": "Electronics",
                    "description": "Good quality",
                    "imageUrl": "https://example.com/img.jpg",
                    "archived": False,
                    "createdAt": "2026-06-21T22:30:20Z",
                    "updatedAt": "2026-06-21T22:30:20Z",
                    "prices": [],
                    "lowestPrice": 250.00,
                    "highestPrice": 350.00
                }
            ],
            "number": 0,
            "size": 10,
            "totalElements": 1,
            "totalPages": 1,
            "last": True,
            "first": True,
            "empty": False
        },
        status=200
    )
    
    result = client.products.search(keyword="Sony")
    assert isinstance(result, PageResponse)
    assert len(result.content) == 1
    item = result.content[0]
    assert isinstance(item, ProductSearchResult)
    assert item.name == "Sony Headphones"
    assert item.lowest_price == Decimal("250.00")
