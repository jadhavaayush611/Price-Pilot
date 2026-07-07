from dataclasses import dataclass

@dataclass
class PricePilotConfig:
    """Configuration class for PricePilotClient.
    
    Attributes:
        base_url: The base URL of the PricePilot API. Defaults to http://localhost:8080/api/v1.
        timeout: Default timeout in seconds for API requests. Defaults to 10.0.
        max_retries: The number of times to retry failed requests. Defaults to 3.
        backoff_factor: A backoff factor to apply between retry attempts. Defaults to 0.3.
        verify_ssl: Whether to verify SSL certificates. Defaults to True.
    """
    base_url: str = "http://localhost:8080/api/v1"
    timeout: float = 10.0
    max_retries: int = 3
    backoff_factor: float = 0.3
    verify_ssl: bool = True
