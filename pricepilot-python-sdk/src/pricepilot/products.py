import logging
from typing import List, Optional, Union
from decimal import Decimal
from pricepilot.http import HttpClientSession
from pricepilot.utils import clean_params
from pricepilot.models import (
    ProductResponse,
    ProductSearchResult,
    PageResponse,
    KeysetPageResponse,
    SellerResponse,
    ProductPriceResponse
)

logger = logging.getLogger("pricepilot.products")

class ProductsModule:
    """Module for Product, Seller, and Price operations."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    # --- Product Operations ---
    def create_product(
        self,
        name: str,
        brand: str,
        category: str,
        description: Optional[str] = None,
        image_url: Optional[str] = None
    ) -> ProductResponse:
        logger.info(f"Creating product: {name}")
        payload = {
            "name": name,
            "brand": brand,
            "category": category,
            "description": description,
            "imageUrl": image_url
        }
        res = self._http.request("POST", "/products", json=payload)
        return ProductResponse.from_dict(res)

    def get_product(self, product_id: str) -> ProductResponse:
        logger.info(f"Fetching product details for ID: {product_id}")
        res = self._http.request("GET", f"/products/{product_id}")
        return ProductResponse.from_dict(res)

    def list_products(
        self,
        search: Optional[str] = None,
        page: int = 0,
        size: int = 10,
        sort: Optional[str] = None
    ) -> PageResponse:
        logger.info(f"Listing products: page={page}, size={size}, search={search}")
        params = clean_params({
            "search": search,
            "page": page,
            "size": size,
            "sort": sort
        })
        res = self._http.request("GET", "/products", params=params)
        return PageResponse.from_dict(res, ProductResponse)

    def update_product(
        self,
        product_id: str,
        name: str,
        brand: str,
        category: str,
        description: Optional[str] = None,
        image_url: Optional[str] = None
    ) -> ProductResponse:
        logger.info(f"Updating product ID: {product_id}")
        payload = {
            "name": name,
            "brand": brand,
            "category": category,
            "description": description,
            "imageUrl": image_url
        }
        res = self._http.request("PUT", f"/products/{product_id}", json=payload)
        return ProductResponse.from_dict(res)

    def delete_product(self, product_id: str) -> None:
        logger.info(f"Deleting product ID: {product_id}")
        self._http.request("DELETE", f"/products/{product_id}")

    # --- Search ---
    def search(
        self,
        keyword: Optional[str] = None,
        category: Optional[str] = None,
        brand: Optional[str] = None,
        page: int = 0,
        size: int = 10,
        sort: Optional[str] = None
    ) -> PageResponse:
        logger.info(f"Searching products with keyword={keyword}, category={category}, brand={brand}")
        params = clean_params({
            "keyword": keyword,
            "category": category,
            "brand": brand,
            "page": page,
            "size": size,
            "sort": sort
        })
        res = self._http.request("GET", "/search", params=params)
        return PageResponse.from_dict(res, ProductSearchResult)

    # --- Additional Lists ---
    def get_popular_products(self, limit: int = 10) -> List[ProductResponse]:
        logger.info(f"Fetching popular products (limit={limit})")
        res = self._http.request("GET", "/products/popular", params={"limit": limit})
        return [ProductResponse.from_dict(x) for x in res]

    def get_trending_products(self, limit: int = 10) -> List[ProductResponse]:
        logger.info(f"Fetching trending products (limit={limit})")
        res = self._http.request("GET", "/products/trending", params={"limit": limit})
        return [ProductResponse.from_dict(x) for x in res]

    def get_biggest_drops(self, limit: int = 10) -> List[ProductResponse]:
        logger.info(f"Fetching products with biggest drops (limit={limit})")
        res = self._http.request("GET", "/products/biggest-drops", params={"limit": limit})
        return [ProductResponse.from_dict(x) for x in res]

    def get_most_watched(self, limit: int = 10) -> List[ProductResponse]:
        logger.info(f"Fetching most watched products (limit={limit})")
        res = self._http.request("GET", "/products/most-watched", params={"limit": limit})
        return [ProductResponse.from_dict(x) for x in res]

    def get_most_saved(self, limit: int = 10) -> List[ProductResponse]:
        logger.info(f"Fetching most saved products (limit={limit})")
        res = self._http.request("GET", "/products/most-saved", params={"limit": limit})
        return [ProductResponse.from_dict(x) for x in res]

    def get_keyset_products(
        self,
        cursor: Optional[str] = None,
        limit: int = 10,
        direction: str = "next"
    ) -> KeysetPageResponse:
        logger.info(f"Fetching keyset products: cursor={cursor}, limit={limit}, direction={direction}")
        params = clean_params({
            "cursor": cursor,
            "limit": limit,
            "direction": direction
        })
        res = self._http.request("GET", "/products/keyset", params=params)
        return KeysetPageResponse.from_dict(res, ProductResponse)

    # --- Seller Operations ---
    def create_seller(
        self,
        name: str,
        website_url: str,
        logo_url: Optional[str] = None
    ) -> SellerResponse:
        logger.info(f"Creating seller: {name}")
        payload = {
            "name": name,
            "websiteUrl": website_url,
            "logoUrl": logo_url
        }
        res = self._http.request("POST", "/sellers", json=payload)
        return SellerResponse.from_dict(res)

    def get_seller(self, seller_id: str) -> SellerResponse:
        logger.info(f"Fetching seller ID: {seller_id}")
        res = self._http.request("GET", f"/sellers/{seller_id}")
        return SellerResponse.from_dict(res)

    def list_sellers(
        self,
        search: Optional[str] = None,
        page: int = 0,
        size: int = 10,
        sort: Optional[str] = None
    ) -> PageResponse:
        logger.info(f"Listing sellers: page={page}, size={size}")
        params = clean_params({
            "search": search,
            "page": page,
            "size": size,
            "sort": sort
        })
        res = self._http.request("GET", "/sellers", params=params)
        return PageResponse.from_dict(res, SellerResponse)

    def update_seller(
        self,
        seller_id: str,
        name: str,
        website_url: str,
        logo_url: Optional[str] = None
    ) -> SellerResponse:
        logger.info(f"Updating seller ID: {seller_id}")
        payload = {
            "name": name,
            "websiteUrl": website_url,
            "logoUrl": logo_url
        }
        res = self._http.request("PUT", f"/sellers/{seller_id}", json=payload)
        return SellerResponse.from_dict(res)

    def delete_seller(self, seller_id: str) -> None:
        logger.info(f"Deleting seller ID: {seller_id}")
        self._http.request("DELETE", f"/sellers/{seller_id}")

    # --- Price Operations ---
    def add_price(
        self,
        product_id: str,
        seller_id: str,
        current_price: Union[float, Decimal, str],
        original_price: Union[float, Decimal, str],
        product_url: str
    ) -> ProductPriceResponse:
        logger.info(f"Adding price to product {product_id} from seller {seller_id}")
        payload = {
            "productId": product_id,
            "sellerId": seller_id,
            "currentPrice": float(current_price),
            "originalPrice": float(original_price),
            "productUrl": product_url
        }
        res = self._http.request("POST", "/prices", json=payload)
        return ProductPriceResponse.from_dict(res)

    def get_price(self, price_id: str) -> ProductPriceResponse:
        logger.info(f"Fetching price ID: {price_id}")
        res = self._http.request("GET", f"/prices/{price_id}")
        return ProductPriceResponse.from_dict(res)

    def list_prices(
        self,
        search: Optional[str] = None,
        page: int = 0,
        size: int = 10,
        sort: Optional[str] = None
    ) -> PageResponse:
        logger.info(f"Listing prices: page={page}, size={size}")
        params = clean_params({
            "search": search,
            "page": page,
            "size": size,
            "sort": sort
        })
        res = self._http.request("GET", "/prices", params=params)
        return PageResponse.from_dict(res, ProductPriceResponse)

    def update_price(
        self,
        price_id: str,
        current_price: Union[float, Decimal, str],
        original_price: Union[float, Decimal, str],
        product_url: str
    ) -> ProductPriceResponse:
        logger.info(f"Updating price ID: {price_id}")
        payload = {
            "currentPrice": float(current_price),
            "originalPrice": float(original_price),
            "productUrl": product_url
        }
        res = self._http.request("PUT", f"/prices/{price_id}", json=payload)
        return ProductPriceResponse.from_dict(res)

    def delete_price(self, price_id: str) -> None:
        logger.info(f"Deleting price ID: {price_id}")
        self._http.request("DELETE", f"/prices/{price_id}")
