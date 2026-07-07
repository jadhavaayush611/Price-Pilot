import pytest
import responses
import pandas as pd
from datetime import datetime
from pricepilot import PricePilotClient

BASE_URL = "http://mock-api.local/api/v1"

@pytest.fixture
def client():
    cli = PricePilotClient(base_url=BASE_URL)
    cli.set_token("mock-token")
    return cli

@responses.activate
def test_get_products_json(client):
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/products",
        json={
            "content": [
                {
                    "id": "prod-1",
                    "name": "Product 1",
                    "brand": "Brand 1",
                    "category": "Cat 1",
                    "description": "Desc 1",
                    "archived": False,
                    "createdAt": "2026-07-07T20:00:00",
                    "updatedAt": "2026-07-07T20:00:00",
                    "currentMinPrice": 100.0,
                    "currentMaxPrice": 120.0,
                    "originalMinPrice": 150.0,
                    "originalMaxPrice": 160.0,
                    "averageDiscountPercentage": 25.0,
                    "sellerCount": 2,
                    "averageSellerRating": 4.5
                }
            ],
            "totalPages": 1,
            "totalElements": 1,
            "number": 0,
            "size": 10
        },
        status=200
    )

    res = client.datasets.get_products(category="Cat 1", brand="Brand 1", archived=False, format="json")
    assert "content" in res
    assert len(res["content"]) == 1
    assert res["content"][0]["name"] == "Product 1"

@responses.activate
def test_get_products_csv(client):
    csv_data = "id,name,brand,category,description,archived,createdAt,updatedAt,currentMinPrice,currentMaxPrice,originalMinPrice,originalMaxPrice,averageDiscountPercentage,sellerCount,averageSellerRating\nprod-1,Product 1,Brand 1,Cat 1,Desc 1,false,2026-07-07T20:00:00,2026-07-07T20:00:00,100.0,120.0,150.0,160.0,25.0,2,4.5"
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/products",
        body=csv_data,
        content_type="text/csv",
        status=200
    )

    res = client.datasets.get_products(category="Cat 1", brand="Brand 1", archived=False, format="csv")
    assert isinstance(res, str)
    assert "Product 1" in res

@responses.activate
def test_products_dataframe(client):
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/products",
        json={
            "content": [
                {
                    "id": "prod-1",
                    "name": "Product 1",
                    "brand": "Brand 1",
                    "category": "Cat 1",
                    "description": "Desc 1",
                    "archived": False,
                    "createdAt": "2026-07-07T20:00:00",
                    "updatedAt": "2026-07-07T20:00:00",
                    "currentMinPrice": 100.0,
                    "currentMaxPrice": 120.0,
                    "originalMinPrice": 150.0,
                    "originalMaxPrice": 160.0,
                    "averageDiscountPercentage": 25.0,
                    "sellerCount": 2,
                    "averageSellerRating": 4.5
                }
            ],
            "totalPages": 1,
            "totalElements": 1,
            "number": 0,
            "size": 100
        },
        status=200
    )

    df = client.datasets.products_dataframe(category="Cat 1")
    assert isinstance(df, pd.DataFrame)
    assert len(df) == 1
    assert df.loc[0, "name"] == "Product 1"
    assert df.loc[0, "currentMinPrice"] == 100.0

@responses.activate
def test_all_dataset_endpoints(client):
    # 1. Product Analytics
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/product-analytics",
        json={"content": [{"productId": "p1", "viewCount": 10, "trendingScore": 15.5}], "totalPages": 1},
        status=200
    )
    df_analytics = client.datasets.product_analytics_dataframe()
    assert len(df_analytics) == 1
    assert df_analytics.loc[0, "viewCount"] == 10

    # 2. Interaction Events
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/interaction-events",
        json={"content": [{"userId": "u1", "interactionType": "PRODUCT_VIEW"}], "totalPages": 1},
        status=200
    )
    df_events = client.datasets.interaction_events_dataframe()
    assert len(df_events) == 1
    assert df_events.loc[0, "interactionType"] == "PRODUCT_VIEW"

    # 3. Watchlists
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/watchlists",
        json={"content": [{"userId": "u1", "productId": "p1", "targetPrice": 80.0}], "totalPages": 1},
        status=200
    )
    df_watchlists = client.datasets.watchlists_dataframe()
    assert len(df_watchlists) == 1
    assert df_watchlists.loc[0, "targetPrice"] == 80.0

    # 4. Saved Products
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/saved-products",
        json={"content": [{"userId": "u1", "productId": "p1"}], "totalPages": 1},
        status=200
    )
    df_saved = client.datasets.saved_products_dataframe()
    assert len(df_saved) == 1
    assert df_saved.loc[0, "productId"] == "p1"

    # 5. Search History
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/search-history",
        json={"content": [{"userId": "u1", "keyword": "sony"}], "totalPages": 1},
        status=200
    )
    df_search = client.datasets.search_history_dataframe()
    assert len(df_search) == 1
    assert df_search.loc[0, "keyword"] == "sony"

    # 6. Dashboard Summary
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/dashboard-summary",
        json={"content": [{"userId": "u1", "savedCount": 5}], "totalPages": 1},
        status=200
    )
    df_dashboard = client.datasets.dashboard_summary_dataframe()
    assert len(df_dashboard) == 1
    assert df_dashboard.loc[0, "savedCount"] == 5

    # 7. Price History
    responses.add(
        responses.GET,
        f"{BASE_URL}/datasets/price-history",
        json={"content": [{"productId": "p1", "oldPrice": 100.0, "newPrice": 90.0}], "totalPages": 1},
        status=200
    )
    df_price = client.datasets.price_history_dataframe()
    assert len(df_price) == 1
    assert df_price.loc[0, "oldPrice"] == 100.0
