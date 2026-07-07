import logging
from typing import List, Optional, Union
from decimal import Decimal
from pricepilot.http import HttpClientSession
from pricepilot.models import WatchlistResponse, SavedProductResponse

logger = logging.getLogger("pricepilot.watchlists")

class WatchlistsModule:
    """Module for managing price watchlists and saved (favorite) products."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    # --- Price Watchlists (CRUD) ---
    def create_watchlist_item(
        self,
        product_id: str,
        target_price: Union[float, Decimal, str]
    ) -> WatchlistResponse:
        """Creates a new price drop watchlist item for a product."""
        logger.info(f"Creating watchlist item for product {product_id} with target {target_price}")
        payload = {
            "productId": product_id,
            "targetPrice": float(target_price)
        }
        res = self._http.request("POST", "/watchlists", json=payload)
        return WatchlistResponse.from_dict(res)

    def update_watchlist_item(
        self,
        watchlist_id: str,
        target_price: Union[float, Decimal, str],
        active: Optional[bool] = None
    ) -> WatchlistResponse:
        """Updates the target price and active status of a watchlist item."""
        logger.info(f"Updating watchlist item {watchlist_id}: target={target_price}, active={active}")
        payload = {
            "targetPrice": float(target_price)
        }
        if active is not None:
            payload["active"] = active
            
        res = self._http.request("PUT", f"/watchlists/{watchlist_id}", json=payload)
        return WatchlistResponse.from_dict(res)

    def delete_watchlist_item(self, watchlist_id: str) -> None:
        """Deletes a watchlist item."""
        logger.info(f"Deleting watchlist item: {watchlist_id}")
        self._http.request("DELETE", f"/watchlists/{watchlist_id}")

    def get_watchlist_item(self, watchlist_id: str) -> WatchlistResponse:
        """Retrieves details for a specific watchlist item."""
        logger.info(f"Fetching watchlist item: {watchlist_id}")
        res = self._http.request("GET", f"/watchlists/{watchlist_id}")
        return WatchlistResponse.from_dict(res)

    def list_watchlist_items(self) -> List[WatchlistResponse]:
        """Lists all watchlist items for the authenticated user."""
        logger.info("Listing all watchlist items")
        res = self._http.request("GET", "/watchlists")
        return [WatchlistResponse.from_dict(x) for x in res]

    # --- Saved Products (Favorites) ---
    def save_product(self, product_id: str) -> None:
        """Saves (favorites) a product for the authenticated user."""
        logger.info(f"Saving product: {product_id}")
        self._http.request("POST", f"/users/saved-products/{product_id}")

    def unsave_product(self, product_id: str) -> None:
        """Removes a product from the authenticated user's saved list."""
        logger.info(f"Removing saved product: {product_id}")
        self._http.request("DELETE", f"/users/saved-products/{product_id}")

    def list_saved_products(self) -> List[SavedProductResponse]:
        """Lists all saved products for the authenticated user."""
        logger.info("Listing all saved products")
        res = self._http.request("GET", "/users/saved-products")
        return [SavedProductResponse.from_dict(x) for x in res]
