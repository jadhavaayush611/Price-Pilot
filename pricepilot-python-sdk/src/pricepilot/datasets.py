import logging
from datetime import datetime, date
from typing import Any, Dict, List, Optional, Union
from pricepilot.http import HttpClientSession

logger = logging.getLogger("pricepilot.datasets")

def _format_datetime(val: Any) -> Optional[str]:
    if val is None:
        return None
    if isinstance(val, (datetime, date)):
        return val.isoformat()
    return str(val)

class DatasetsModule:
    """Module for retrieving normalized datasets for Machine Learning and analytics."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    # --- Raw API Fetch Methods ---

    def get_products(
        self,
        category: Optional[str] = None,
        brand: Optional[str] = None,
        archived: Optional[bool] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches product dataset."""
        logger.info("Fetching products dataset")
        params = {
            "format": format
        }
        if category is not None:
            params["category"] = category
        if brand is not None:
            params["brand"] = brand
        if archived is not None:
            params["archived"] = str(archived).lower()
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/products", params=params)

    def get_product_analytics(
        self,
        product_id: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches product analytics dataset."""
        logger.info("Fetching product analytics dataset")
        params = {
            "format": format
        }
        if product_id is not None:
            params["productId"] = product_id
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/product-analytics", params=params)

    def get_interaction_events(
        self,
        user_id: Optional[str] = None,
        product_id: Optional[str] = None,
        seller_id: Optional[str] = None,
        type: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches user interaction events dataset."""
        logger.info("Fetching interaction events dataset")
        params = {
            "format": format
        }
        if user_id is not None:
            params["userId"] = user_id
        if product_id is not None:
            params["productId"] = product_id
        if seller_id is not None:
            params["sellerId"] = seller_id
        if type is not None:
            params["type"] = type
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/interaction-events", params=params)

    def get_watchlists(
        self,
        user_id: Optional[str] = None,
        product_id: Optional[str] = None,
        active: Optional[bool] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches watchlists dataset."""
        logger.info("Fetching watchlists dataset")
        params = {
            "format": format
        }
        if user_id is not None:
            params["userId"] = user_id
        if product_id is not None:
            params["productId"] = product_id
        if active is not None:
            params["active"] = str(active).lower()
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/watchlists", params=params)

    def get_saved_products(
        self,
        user_id: Optional[str] = None,
        product_id: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches saved products dataset."""
        logger.info("Fetching saved products dataset")
        params = {
            "format": format
        }
        if user_id is not None:
            params["userId"] = user_id
        if product_id is not None:
            params["productId"] = product_id
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/saved-products", params=params)

    def get_search_history(
        self,
        user_id: Optional[str] = None,
        keyword: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches search history dataset."""
        logger.info("Fetching search history dataset")
        params = {
            "format": format
        }
        if user_id is not None:
            params["userId"] = user_id
        if keyword is not None:
            params["keyword"] = keyword
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/search-history", params=params)

    def get_dashboard_summary(
        self,
        user_id: Optional[str] = None,
        role: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches user dashboard summary metrics dataset."""
        logger.info("Fetching dashboard summary dataset")
        params = {
            "format": format
        }
        if user_id is not None:
            params["userId"] = user_id
        if role is not None:
            params["role"] = role
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/dashboard-summary", params=params)

    def get_price_history(
        self,
        product_id: Optional[str] = None,
        seller_id: Optional[str] = None,
        start_date: Optional[Union[datetime, str]] = None,
        end_date: Optional[Union[datetime, str]] = None,
        page: Optional[int] = None,
        size: Optional[int] = None,
        sort: Optional[str] = None,
        format: str = "json"
    ) -> Any:
        """Fetches price history changes dataset."""
        logger.info("Fetching price history dataset")
        params = {
            "format": format
        }
        if product_id is not None:
            params["productId"] = product_id
        if seller_id is not None:
            params["sellerId"] = seller_id
        if start_date is not None:
            params["startDate"] = _format_datetime(start_date)
        if end_date is not None:
            params["endDate"] = _format_datetime(end_date)
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort is not None:
            params["sort"] = sort

        return self._http.request("GET", "/datasets/price-history", params=params)

    # --- Pandas DataFrame Conversion Helpers ---

    def _to_dataframe(self, fetch_fn, fetch_kwargs) -> Any:
        try:
            import pandas as pd
        except ImportError:
            raise ImportError("pandas is required for DataFrame conversion. Install it using 'pip install pandas'.")

        page = fetch_kwargs.get("page")
        size = fetch_kwargs.get("size")

        if page is not None:
            # Single page requested
            fetch_kwargs["format"] = "json"
            res = fetch_fn(**fetch_kwargs)
            records = res.get("content", [])
        else:
            # Fetch all pages recursively
            records = []
            current_page = 0
            page_size = size or 100
            fetch_kwargs["format"] = "json"
            fetch_kwargs["size"] = page_size

            while True:
                fetch_kwargs["page"] = current_page
                res = fetch_fn(**fetch_kwargs)
                content = res.get("content", [])
                records.extend(content)

                total_pages = res.get("totalPages", 1)
                if not content or len(content) < page_size or current_page >= total_pages - 1:
                    break
                current_page += 1

        return pd.DataFrame(records)

    def products_dataframe(self, **kwargs) -> Any:
        """Fetches products dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_products, kwargs)

    def product_analytics_dataframe(self, **kwargs) -> Any:
        """Fetches product analytics dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_product_analytics, kwargs)

    def interaction_events_dataframe(self, **kwargs) -> Any:
        """Fetches user interaction events dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_interaction_events, kwargs)

    def watchlists_dataframe(self, **kwargs) -> Any:
        """Fetches watchlists dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_watchlists, kwargs)

    def saved_products_dataframe(self, **kwargs) -> Any:
        """Fetches saved products dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_saved_products, kwargs)

    def search_history_dataframe(self, **kwargs) -> Any:
        """Fetches search history dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_search_history, kwargs)

    def dashboard_summary_dataframe(self, **kwargs) -> Any:
        """Fetches user dashboard summary metrics dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_dashboard_summary, kwargs)

    def price_history_dataframe(self, **kwargs) -> Any:
        """Fetches price history changes dataset and returns a Pandas DataFrame."""
        return self._to_dataframe(self.get_price_history, kwargs)
