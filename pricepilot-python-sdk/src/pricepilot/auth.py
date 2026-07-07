import logging
from pricepilot.http import HttpClientSession
from pricepilot.models import AuthResponse

logger = logging.getLogger("pricepilot.auth")

class AuthModule:
    """Module for authentication actions (login, logout, refresh, register)."""
    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client
        
    def login(self, email: str, password: str) -> AuthResponse:
        """Authenticates a user with email and password.
        
        Args:
            email: User's email.
            password: User's password.
            
        Returns:
            AuthResponse containing the JWT token and user details.
            
        Raises:
            AuthenticationError: If credentials are invalid.
            ValidationError: If input validation fails.
            PricePilotError: If request fails.
        """
        logger.info(f"Attempting login for user: {email}")
        payload = {
            "email": email,
            "password": password
        }
        res = self._http.request("POST", "/auth/login", json=payload)
        auth_res = AuthResponse.from_dict(res)
        self._http.set_token(auth_res.token)
        logger.info(f"Login successful for user: {email}")
        return auth_res
        
    def register(self, email: str, password: str, first_name: str, last_name: str) -> AuthResponse:
        """Registers a new user and authenticates them.
        
        Args:
            email: User's email.
            password: User's password.
            first_name: User's first name.
            last_name: User's last name.
            
        Returns:
            AuthResponse containing the JWT token and user details.
            
        Raises:
            ValidationError: If email is invalid or already taken, or password is too short.
            PricePilotError: If request fails.
        """
        logger.info(f"Attempting registration for user: {email}")
        payload = {
            "email": email,
            "password": password,
            "firstName": first_name,
            "lastName": last_name
        }
        res = self._http.request("POST", "/auth/register", json=payload)
        auth_res = AuthResponse.from_dict(res)
        self._http.set_token(auth_res.token)
        logger.info(f"Registration successful for user: {email}")
        return auth_res
        
    def logout(self) -> None:
        """Logs out the user by clearing the local session token."""
        logger.info("Logging out and clearing session token.")
        self._http.clear_token()
        
    def refresh_token(self) -> None:
        """Refreshes the authentication token.
        
        Note: The PricePilot backend REST API does not currently expose a token refresh endpoint.
        Calling this method will raise a NotImplementedError.
        """
        logger.error("Token refresh is not supported by the backend API.")
        raise NotImplementedError("Token refresh is not supported by the PricePilot REST API.")
