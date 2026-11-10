class ProviderError(Exception):
    """Base exception for all LLM provider errors."""
    def __init__(self, message: str, provider: str = "unknown", status_code: int = 500, raw_error: str = ""):
        super().__init__(message)
        self.provider = provider
        self.status_code = status_code
        self.raw_error = raw_error

class ProviderAuthError(ProviderError):
    """Raised when provider authentication or authorization fails (401/403)."""
    def __init__(self, message: str, provider: str = "unknown", status_code: int = 401, raw_error: str = ""):
        super().__init__(message, provider=provider, status_code=status_code, raw_error=raw_error)

class ProviderRateLimitError(ProviderError):
    """Raised when upstream rate limits or quotas are exhausted (429)."""
    def __init__(self, message: str, provider: str = "unknown", retry_after: float = 0.0, raw_error: str = ""):
        super().__init__(message, provider=provider, status_code=429, raw_error=raw_error)
        self.retry_after = retry_after

class ProviderTimeoutError(ProviderError):
    """Raised when provider operation exceeds configured budget (3.5s maximum)."""
    def __init__(self, message: str, provider: str = "unknown", elapsed_seconds: float = 0.0):
        super().__init__(message, provider=provider, status_code=504, raw_error="TIMEOUT")
        self.elapsed_seconds = elapsed_seconds

class ProviderInvalidRequestError(ProviderError):
    """Raised when upstream rejects request due to invalid parameters or formatting (400)."""
    def __init__(self, message: str, provider: str = "unknown", raw_error: str = ""):
        super().__init__(message, provider=provider, status_code=400, raw_error=raw_error)

class ProviderContentFilterError(ProviderError):
    """Raised when provider blocks generation due to safety or policy filters."""
    def __init__(self, message: str, provider: str = "unknown", raw_error: str = ""):
        super().__init__(message, provider=provider, status_code=422, raw_error=raw_error)

class ProviderUnavailableError(ProviderError):
    """Raised when upstream provider service is overloaded or unavailable (500/503)."""
    def __init__(self, message: str, provider: str = "unknown", status_code: int = 503, raw_error: str = ""):
        super().__init__(message, provider=provider, status_code=status_code, raw_error=raw_error)

class StructuredOutputValidationError(ProviderError):
    """Raised when model output fails Pydantic schema validation."""
    def __init__(self, message: str, provider: str = "unknown", validation_errors: str = ""):
        super().__init__(message, provider=provider, status_code=502, raw_error=validation_errors)
        self.validation_errors = validation_errors
