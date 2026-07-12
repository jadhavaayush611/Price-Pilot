import logging
import requests
import re
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

def sanitize_secrets(text: str) -> str:
    if not text:
        return text
    # Redact Bearer tokens
    text = re.sub(r'Bearer\s+[A-Za-z0-9\-._~+/]+=*', 'Bearer [REDACTED]', text, flags=re.IGNORECASE)
    # Redact X-API-Key / API keys
    text = re.sub(r'([a-zA-Z0-9\-_]*api[a-zA-Z0-9\-_]*key[a-zA-Z0-9\-_]*[\s:=]+)[a-zA-Z0-9\-._~+/]+', r'\1[REDACTED]', text, flags=re.IGNORECASE)
    # Redact password fields in JSON or query params
    text = re.sub(r'("password"\s*:\s*")[^"]+(")', r'\1[REDACTED]\2', text, flags=re.IGNORECASE)
    text = re.sub(r'(password=)[a-zA-Z0-9\-._~+/]+', r'\1[REDACTED]', text, flags=re.IGNORECASE)
    return text

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
        
    def _build_url(self, path: str, is_ai: bool = False) -> str:
        if is_ai:
            base = self.config.ai_base_url.rstrip("/")
            # Remove any /api/v1 prefix if FastAPI endpoints are root-level
            sub = path.lstrip("/")
            return f"{base}/{sub}"
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
        timeout: Optional[float] = None,
        is_ai: bool = False
    ) -> Any:
        url = self._build_url(path, is_ai=is_ai)
        
        # We pass request-specific headers directly to the requests.request call
        # so they do NOT leak to the persistent session headers
        req_headers = {}
        if is_ai:
            req_headers["X-API-Key"] = self.config.ai_api_key
        if headers:
            req_headers.update(headers)
            
        use_timeout = timeout if timeout is not None else self.config.timeout
        
        logged_params = sanitize_secrets(str(params)) if params else None
        logger.debug(f"Sending request: {method} {url} | Params: {logged_params}")
        
        try:
            response = self.session.request(
                method=method,
                url=url,
                params=params,
                json=json,
                headers=req_headers,
                timeout=use_timeout,
                verify=self.config.verify_ssl
            )
        except requests.exceptions.Timeout as e:
            logger.error(f"Request timeout: {method} {url}")
            sanitized_e = sanitize_secrets(str(e))
            raise ApiError(f"Request timed out after {use_timeout}s: {sanitized_e}", status_code=408) from e
        except requests.exceptions.RequestException as e:
            logger.error(f"Request failed: {method} {url}")
            sanitized_e = sanitize_secrets(str(e))
            raise PricePilotError(f"HTTP request failed: {sanitized_e}") from e
            
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
            
        message = sanitize_secrets(message)
        response_text = sanitize_secrets(response.text)
        
        logger.warning(f"Error response {status_code} from {url} | Message: {message}")
        
        if status_code in (401, 403):
            raise AuthenticationError(message)
        elif status_code == 404:
            raise NotFoundError(message)
        elif status_code in (400, 422):
            raise ValidationError(message, validation_errors=validation_errors)
        else:
            raise ApiError(f"API Error ({status_code}): {message}", status_code=status_code, response_body=response_text)
