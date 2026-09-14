import logging
from typing import Optional, Dict, Any
from decimal import Decimal
from pricepilot.http import HttpClientSession
from pricepilot.utils import clean_params
from pricepilot.models import (
    AlternativeResponseModel,
    AlternativeProductModel,
    AlternativeEvidenceModel
)

logger = logging.getLogger("pricepilot.alternatives")

class AlternativesModule:
    """Module for Product Alternatives Discovery."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def find_alternatives_for_product(
        self,
        product_id: str,
        type: str = "SIMILAR",
        category: Optional[str] = None,
        brand: Optional[str] = None,
        min_price: Optional[float] = None,
        max_price: Optional[float] = None,
        min_rating: Optional[float] = None,
        min_discount: Optional[float] = None,
        in_stock: Optional[bool] = None,
        min_semantic_similarity: Optional[float] = None,
        limit: int = 10
    ) -> AlternativeResponseModel:
        """Discovers alternatives for a given product baseline."""
        logger.info(f"Finding {type} alternatives for product ID: {product_id}")
        params = clean_params({
            "type": type,
            "category": category,
            "brand": brand,
            "minPrice": min_price,
            "maxPrice": max_price,
            "minRating": min_rating,
            "minDiscount": min_discount,
            "inStock": in_stock,
            "minSemanticSimilarity": min_semantic_similarity,
            "limit": limit
        })
        res = self._http.request("GET", f"/alternatives/product/{product_id}", params=params)
        return AlternativeResponseModel.from_dict(res)

    def find_alternatives_for_query(
        self,
        query: str,
        type: str = "SIMILAR",
        category: Optional[str] = None,
        brand: Optional[str] = None,
        min_price: Optional[float] = None,
        max_price: Optional[float] = None,
        min_rating: Optional[float] = None,
        min_discount: Optional[float] = None,
        in_stock: Optional[bool] = None,
        limit: int = 10
    ) -> AlternativeResponseModel:
        """Discovers alternatives from structured or search query intent."""
        logger.info(f"Finding {type} alternatives for query: {query}")
        params = clean_params({
            "query": query,
            "type": type,
            "category": category,
            "brand": brand,
            "minPrice": min_price,
            "maxPrice": max_price,
            "minRating": min_rating,
            "minDiscount": min_discount,
            "inStock": in_stock,
            "limit": limit
        })
        res = self._http.request("GET", "/alternatives/query", params=params)
        return AlternativeResponseModel.from_dict(res)

    def find_alternatives_natural_language(
        self,
        query: str,
        type: Optional[str] = None,
        limit: int = 10
    ) -> AlternativeResponseModel:
        """Discovers alternatives from a free-form natural language query."""
        logger.info(f"Finding NL alternatives: {query}")
        params = clean_params({
            "q": query,
            "type": type,
            "limit": limit
        })
        res = self._http.request("GET", "/alternatives/natural-language", params=params)
        return AlternativeResponseModel.from_dict(res)
