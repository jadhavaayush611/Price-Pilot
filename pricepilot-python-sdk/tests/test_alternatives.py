import pytest
from unittest.mock import MagicMock
from decimal import Decimal
from pricepilot.client import PricePilotClient
from pricepilot.models import AlternativeResponseModel

def test_find_alternatives_for_product():
    client = PricePilotClient(base_url="http://testserver/api/v1")
    client._http.request = MagicMock(return_value={
        "executionMode": "PRODUCT",
        "alternativeType": "CHEAPER",
        "totalFound": 1,
        "content": [{
            "id": "11111111-1111-1111-1111-111111111111",
            "name": "Alternative Headphone",
            "brand": "Anker",
            "category": "Headphones",
            "currentBestPrice": 149.99,
            "alternativeScore": 85.5,
            "priceDifference": -250.00,
            "priceDifferencePercentage": -62.5,
            "reasonCodes": ["LOWER_PRICE", "HIGH_SEMANTIC_SIMILARITY"],
            "evidence": [{
                "category": "PRICE",
                "relationship": "CHEAPER",
                "description": "Saves $250.00 (62.5% less)"
            }],
            "primaryExplanation": "Saves $250.00 (62% cheaper) with 88% concept similarity."
        }]
    })

    res = client.alternatives.find_alternatives_for_product("00000000-0000-0000-0000-000000000000", type="CHEAPER")
    assert isinstance(res, AlternativeResponseModel)
    assert res.execution_mode == "PRODUCT"
    assert res.alternative_type == "CHEAPER"
    assert len(res.content) == 1
    assert res.content[0].name == "Alternative Headphone"
    assert res.content[0].price_difference == Decimal("-250.00")
    assert "LOWER_PRICE" in res.content[0].reason_codes


def test_find_alternatives_for_query():
    client = PricePilotClient(base_url="http://testserver/api/v1")
    client._http.request = MagicMock(return_value={
        "executionMode": "QUERY",
        "alternativeType": "BETTER_VALUE",
        "totalFound": 1,
        "content": [{
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Value Laptop",
            "brand": "Lenovo",
            "category": "Laptops",
            "currentBestPrice": 799.00,
            "alternativeScore": 90.0,
            "badges": ["Best Value"]
        }]
    })

    res = client.alternatives.find_alternatives_for_query("laptop under $1000", type="BETTER_VALUE")
    assert res.execution_mode == "QUERY"
    assert len(res.content) == 1
    assert res.content[0].name == "Value Laptop"
