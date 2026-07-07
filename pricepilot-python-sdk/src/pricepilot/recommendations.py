import logging
from typing import List, Optional, Union
from decimal import Decimal
from pricepilot.http import HttpClientSession
from pricepilot.utils import clean_params
from pricepilot.models import ProductResponse, PageResponse

logger = logging.getLogger("pricepilot.recommendations")

class RecommendationsModule:
    """Module for retrieving personalized, similar, and trending recommendations."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def get_recommendations(
        self,
        category: Optional[str] = None,
        brand: Optional[str] = None,
        min_price: Optional[Union[float, Decimal, str]] = None,
        max_price: Optional[Union[float, Decimal, str]] = None,
        sort: Optional[str] = None,
        page: int = 0,
        size: int = 10
    ) -> PageResponse:
        """Retrieves personalized recommendations for the authenticated user.
        
        Requires active authentication session.
        """
        logger.info("Retrieving personalized recommendations")
        params = clean_params({
            "category": category,
            "brand": brand,
            "minPrice": float(min_price) if min_price is not None else None,
            "maxPrice": float(max_price) if max_price is not None else None,
            "sort": sort,
            "page": page,
            "size": size
        })
        res = self._http.request("GET", "/recommendations", params=params)
        return PageResponse.from_dict(res, ProductResponse)

    def get_similar_products(self, product_id: str, limit: int = 10) -> List[ProductResponse]:
        """Retrieves similar products for a given product ID."""
        logger.info(f"Retrieving similar products for product: {product_id} with limit={limit}")
        res = self._http.request(
            "GET",
            f"/recommendations/similar/{product_id}",
            params={"limit": limit}
        )
        return [ProductResponse.from_dict(x) for x in res]

    def get_trending_products(self, limit: int = 10) -> List[ProductResponse]:
        """Retrieves trending products."""
        logger.info(f"Retrieving trending recommendations with limit={limit}")
        res = self._http.request(
            "GET",
            "/recommendations/trending",
            params={"limit": limit}
        )
        return [ProductResponse.from_dict(x) for x in res]
