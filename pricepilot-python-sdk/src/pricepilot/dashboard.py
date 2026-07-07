import logging
from pricepilot.http import HttpClientSession
from pricepilot.models import DashboardData

logger = logging.getLogger("pricepilot.dashboard")

class DashboardModule:
    """Module for retrieving user dashboard summary statistics and activity feeds."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def get_dashboard_data(self) -> DashboardData:
        """Retrieves the aggregated dashboard view for the authenticated user."""
        logger.info("Retrieving user dashboard data")
        res = self._http.request("GET", "/dashboard")
        return DashboardData.from_dict(res)
