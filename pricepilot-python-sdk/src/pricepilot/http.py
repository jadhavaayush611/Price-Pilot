import logging
import requests
from requests.adapters import HTTPAdapter
from urllib3.util import Retry
from typing import Dict, Any, Optional
from pricepilot.config import PricePilotConfig
from pricepilot.exceptions import (
    AuthenticationError,
    ValidationError,
    NotFoundError,
    ApiError,
    PricePilotError
)

logger = logging.getLogger("pricepilot.http")

class HttpClientSession:
    """Internal HTTP Client session wrapping requests.Session with resilient retry policies,
    base URL resolving, authentication token management, and exception wrapping.
    """
    def __init__(self, config: PricePilotConfig) -> None:
        self.config = config
        self.session = requests.Session()
        self._token: Optional[str] = None
        
        # Configure retry strategy
        retry_strategy = Retry(
            total=config.max_retries,
            backoff_factor=config.backoff_factor,
            status_forcelist=[429, 500, 502, 503, 504],
            raise_on_status=False  # Allow us to handle response status ourselves
        )
        adapter = HTTPAdapter(max_retries=retry_strategy)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        
    def set_token(self, token: Optional[str]) -> None:
        """Sets the Bearer token for authentication."""
        self._token = token
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"
        else:
            self.session.headers.pop("Authorization", None)
            
    def clear_token(self) -> None:
        """Removes the authentication token."""
        self.set_token(None)
        
    def _build_url(self, path: str) -> str:
        base = self.config.base_url.rstrip("/")
        sub = path.lstrip("/")
        return f"{base}/{sub}"
        
    def request(
        self,
        method: str,
        path: str,
        params: Optional[Dict[str, Any]] = None,
        json: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, Any]] = None,
        timeout: Optional[float] = None
    ) -> Any:
        url = self._build_url(path)
        req_headers = headers or {}
        
        # Merge extra headers
        for k, v in req_headers.items():
            self.session.headers[k] = v
            
        use_timeout = timeout if timeout is not None else self.config.timeout
        
        logger.debug(f"Sending request: {method} {url} | Params: {params}")
        
        try:
            response = self.session.request(
                method=method,
                url=url,
                params=params,
                json=json,
                timeout=use_timeout,
                verify=self.config.verify_ssl
            )
        except requests.exceptions.Timeout as e:
            logger.error(f"Request timeout: {method} {url}")
            raise ApiError(f"Request timed out after {use_timeout}s: {e}", status_code=408) from e
        except requests.exceptions.RequestException as e:
            logger.error(f"Request failed: {method} {url} | Error: {e}")
            raise PricePilotError(f"HTTP request failed: {e}") from e
            
        logger.debug(f"Received response: {response.status_code} from {method} {url}")
        
        # Handle response errors
        if response.status_code >= 400:
            self._handle_error_response(response)
            
        if response.status_code == 204:
            return None
            
        try:
            return response.json()
        except ValueError as e:
            # Response is not JSON
            return response.text
            
    def _handle_error_response(self, response: requests.Response) -> None:
        status_code = response.status_code
        url = response.url
        
        try:
            error_data = response.json()
            message = error_data.get("message", f"HTTP Error {status_code}")
            validation_errors = error_data.get("validationErrors", [])
        except ValueError:
            message = response.text or f"HTTP Error {status_code}"
            validation_errors = []
            
        logger.warning(f"Error response {status_code} from {url} | Message: {message}")
        
        if status_code in (401, 403):
            raise AuthenticationError(message)
        elif status_code == 404:
            raise NotFoundError(message)
        elif status_code in (400, 422):
            raise ValidationError(message, validation_errors=validation_errors)
        else:
            raise ApiError(f"API Error ({status_code}): {message}", status_code=status_code, response_body=response.text)
