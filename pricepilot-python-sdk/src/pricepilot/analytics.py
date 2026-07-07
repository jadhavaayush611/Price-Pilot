import logging
from pricepilot.http import HttpClientSession
from pricepilot.models import ProductAnalyticsResponse

logger = logging.getLogger("pricepilot.analytics")

class AnalyticsModule:
    """Module for retrieving product-specific views, saves, watchlists, and price change metrics."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def get_product_analytics(self, product_id: str) -> ProductAnalyticsResponse:
        """Retrieves popularity and activity analytics for a specific product ID."""
        logger.info(f"Retrieving product analytics for ID: {product_id}")
        res = self._http.request("GET", f"/analytics/products/{product_id}")
        return ProductAnalyticsResponse.from_dict(res)
