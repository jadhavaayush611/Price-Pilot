import os
import requests
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("pricepilot.assistant.tools")

BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080/api/v1")

def _get_headers(token: Optional[str] = None) -> Dict[str, str]:
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    if token:
        if token.startswith("Bearer "):
            headers["Authorization"] = token
        else:
            headers["Authorization"] = f"Bearer {token}"
    return headers

def search_products(keyword: str, category: Optional[str] = None, brand: Optional[str] = None, token: Optional[str] = None) -> Dict[str, Any]:
    """Search for products using keywords, categories, and brand filters."""
    url = f"{BACKEND_URL}/search"
    params = {"keyword": keyword}
    if category:
        params["category"] = category
    if brand:
        params["brand"] = brand
        
    try:
        response = requests.get(url, headers=_get_headers(token), params=params, timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Search products failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in search_products tool")
    return {"content": [], "totalElements": 0}

def get_product_details(product_id: str, token: Optional[str] = None) -> Dict[str, Any]:
    """Retrieve details for a specific product by ID."""
    url = f"{BACKEND_URL}/products/{product_id}"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get product details failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_product_details tool")
    return {}

def get_similar_products(product_id: str, limit: int = 10, token: Optional[str] = None) -> List[Dict[str, Any]]:
    """Retrieve similar products for a target product ID."""
    url = f"{BACKEND_URL}/recommendations/similar/{product_id}"
    params = {"limit": limit}
    try:
        response = requests.get(url, headers=_get_headers(token), params=params, timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get similar products failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_similar_products tool")
    return []

def get_recommendations(category: Optional[str] = None, brand: Optional[str] = None, token: Optional[str] = None) -> Dict[str, Any]:
    """Get personalized recommendations based on user preferences."""
    url = f"{BACKEND_URL}/recommendations"
    params = {}
    if category:
        params["category"] = category
    if brand:
        params["brand"] = brand
    try:
        response = requests.get(url, headers=_get_headers(token), params=params, timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get recommendations failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_recommendations tool")
    return {"content": [], "totalElements": 0}

def get_dashboard(token: Optional[str] = None) -> Dict[str, Any]:
    """Retrieve the user's dashboard summary including activity and recent savings."""
    url = f"{BACKEND_URL}/dashboard"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get dashboard failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_dashboard tool")
    return {}

def get_trending_products(limit: int = 10, token: Optional[str] = None) -> List[Dict[str, Any]]:
    """Retrieve trending products."""
    url = f"{BACKEND_URL}/recommendations/trending"
    params = {"limit": limit}
    try:
        response = requests.get(url, headers=_get_headers(token), params=params, timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get trending products failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_trending_products tool")
    return []

def get_product_analytics(product_id: str, token: Optional[str] = None) -> Dict[str, Any]:
    """Get view counts, saves, and watchlist counts for a product."""
    url = f"{BACKEND_URL}/analytics/products/{product_id}"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get product analytics failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_product_analytics tool")
    return {}

def get_price_history(product_id: str, token: Optional[str] = None) -> List[Dict[str, Any]]:
    """Retrieve price history data points for a product."""
    url = f"{BACKEND_URL}/products/{product_id}/price-history"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get price history failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_price_history tool")
    return []

def get_watchlists(token: Optional[str] = None) -> List[Dict[str, Any]]:
    """Retrieve the user's active watchlists."""
    url = f"{BACKEND_URL}/watchlists"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get watchlists failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_watchlists tool")
    return []

def get_saved_products(token: Optional[str] = None) -> List[Dict[str, Any]]:
    """Retrieve the user's saved products list."""
    url = f"{BACKEND_URL}/users/saved-products"
    try:
        response = requests.get(url, headers=_get_headers(token), timeout=5)
        if response.status_code == 200:
            return response.json()
        logger.error(f"Get saved products failed: {response.status_code} - {response.text}")
    except Exception as e:
        logger.exception("Exception in get_saved_products tool")
    return []

# Dictionary of available tools for dynamic invocation
TOOL_MAPPING = {
    "search_products": search_products,
    "get_product_details": get_product_details,
    "get_similar_products": get_similar_products,
    "get_recommendations": get_recommendations,
    "get_dashboard": get_dashboard,
    "get_trending_products": get_trending_products,
    "get_product_analytics": get_product_analytics,
    "get_price_history": get_price_history,
    "get_watchlists": get_watchlists,
    "get_saved_products": get_saved_products
}
