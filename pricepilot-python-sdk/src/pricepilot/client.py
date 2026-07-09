from typing import Optional
from pricepilot.config import PricePilotConfig
from pricepilot.http import HttpClientSession
from pricepilot.auth import AuthModule
from pricepilot.products import ProductsModule
from pricepilot.recommendations import RecommendationsModule
from pricepilot.watchlists import WatchlistsModule
from pricepilot.dashboard import DashboardModule
from pricepilot.analytics import AnalyticsModule
from pricepilot.datasets import DatasetsModule
from pricepilot.ml import MlModule
from pricepilot.ai import AiModule
from pricepilot.assistant import AssistantModule

class PricePilotClient:
    """The main client for the PricePilot Python SDK.
    
    Exposes sub-modules corresponding to PricePilot API resource scopes.
    """
    def __init__(
        self,
        base_url: Optional[str] = None,
        timeout: Optional[float] = None,
        max_retries: Optional[int] = None,
        backoff_factor: Optional[float] = None,
        verify_ssl: Optional[bool] = None,
        config: Optional[PricePilotConfig] = None,
        token: Optional[str] = None
    ) -> None:
        """Initializes the PricePilotClient.
        
        Args:
            base_url: Optional API base URL.
            timeout: Optional HTTP request timeout.
            max_retries: Optional maximum retry attempts for failed requests.
            backoff_factor: Optional retry backoff factor.
            verify_ssl: Optional boolean to enable/disable SSL verification.
            config: Optional pre-configured PricePilotConfig instance.
            token: Optional pre-existing JWT token for authenticating requests.
        """
        if config is None:
            config = PricePilotConfig()
            
        # Override config parameters if provided
        if base_url is not None:
            config.base_url = base_url
        if timeout is not None:
            config.timeout = timeout
        if max_retries is not None:
            config.max_retries = max_retries
        if backoff_factor is not None:
            config.backoff_factor = backoff_factor
        if verify_ssl is not None:
            config.verify_ssl = verify_ssl
            
        self.config = config
        self._http = HttpClientSession(config)
        
        if token:
            self._http.set_token(token)
            
        # Instantiate sub-modules
        self.auth = AuthModule(self._http)
        self.products = ProductsModule(self._http)
        self.recommendations = RecommendationsModule(self._http)
        self.watchlists = WatchlistsModule(self._http)
        self.dashboard = DashboardModule(self._http)
        self.analytics = AnalyticsModule(self._http)
        self.datasets = DatasetsModule(self._http)
        self.ml = MlModule(self._http)
        self.ai = AiModule(self._http)
        self.assistant = AssistantModule(self._http)
        
    def set_token(self, token: Optional[str]) -> None:
        """Sets the active Bearer token for requests."""
        self._http.set_token(token)
        
    def clear_token(self) -> None:
        """Clears the active Bearer token."""
        self._http.clear_token()
