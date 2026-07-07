import logging
import sys
from pricepilot import PricePilotClient, ValidationError, AuthenticationError

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger("demo_auth")

def run():
    client = PricePilotClient(base_url="http://localhost:8080/api/v1")
    
    # 1. Register a new user
    email = "sdk_user_test@example.com"
    password = "password123"
    first_name = "Jane"
    last_name = "Doe"
    
    logger.info(f"Attempting to register user: {email}...")
    try:
        auth_response = client.auth.register(
            email=email,
            password=password,
            first_name=first_name,
            last_name=last_name
        )
        logger.info(f"Registration successful! Token: {auth_response.token[:15]}...")
        logger.info(f"User Details: {auth_response.user}")
    except ValidationError as e:
        logger.warning(f"Registration validation failed (e.g. user already exists): {e}")
        logger.info("Proceeding to login instead.")
    except Exception as e:
        logger.error(f"Failed to connect to PricePilot backend: {e}")
        logger.error("Please make sure the PricePilot server is running at http://localhost:8080")
        sys.exit(1)

    # 2. Login
    logger.info(f"Attempting to login user: {email}...")
    try:
        auth_response = client.auth.login(email=email, password=password)
        logger.info(f"Login successful! Token: {auth_response.token[:15]}...")
    except AuthenticationError as e:
        logger.error(f"Login failed: {e}")
    
    # 3. Logout
    logger.info("Logging out...")
    client.auth.logout()
    logger.info("Logout complete (session token cleared).")

if __name__ == "__main__":
    run()
