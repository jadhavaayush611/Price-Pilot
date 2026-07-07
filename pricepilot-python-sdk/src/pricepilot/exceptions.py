from typing import Dict, Any, List, Optional

class PricePilotError(Exception):
    """Base exception for all PricePilot SDK errors."""
    pass

class AuthenticationError(PricePilotError):
    """Raised when authentication fails (e.g. invalid credentials, expired token)."""
    pass

class ValidationError(PricePilotError):
    """Raised when input parameters fail server-side validation rules (400 Bad Request)."""
    def __init__(self, message: str, validation_errors: Optional[List[Dict[str, Any]]] = None) -> None:
        super().__init__(message)
        self.validation_errors = validation_errors or []

class NotFoundError(PricePilotError):
    """Raised when a requested resource is not found (404 Not Found)."""
    pass

class ApiError(PricePilotError):
    """Raised when the server returns an unexpected error (e.g., 500 Internal Server Error)."""
    def __init__(self, message: str, status_code: int, response_body: Optional[str] = None) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.response_body = response_body
