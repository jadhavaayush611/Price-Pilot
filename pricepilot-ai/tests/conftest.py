import os

# Set environment variables for testing before any application imports
os.environ["ENV"] = "development"
os.environ["PRICEPILOT_AI_API_KEY"] = "pricepilot-secret-api-key"
