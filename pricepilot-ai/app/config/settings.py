import os

class Settings:
    def __init__(self):
        self.env = os.getenv("ENV", "production")
        
        # Validate required secrets in production
        api_key = os.getenv("PRICEPILOT_AI_API_KEY")
        if self.env.lower() in ("prod", "production"):
            if not api_key:
                raise ValueError("CRITICAL: PRICEPILOT_AI_API_KEY environment variable is required in production mode.")
            if api_key == "pricepilot-secret-api-key":
                raise ValueError("CRITICAL: PRICEPILOT_AI_API_KEY cannot use the insecure default fallback in production mode.")
            self.api_key = api_key
        else:
            self.api_key = api_key if api_key else "pricepilot-secret-api-key"
        
        # Paths
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        self.model_dir = os.getenv(
            "MODEL_DIR", 
            os.path.abspath(os.path.join(base_dir, "..", "models", "recommendation"))
        )
        self.dataset_dir = os.getenv(
            "DATASET_DIR", 
            os.path.abspath(os.path.join(base_dir, "..", "datasets", "feature_sets"))
        )
        
        self.host = os.getenv("HOST", "0.0.0.0")
        self.port = int(os.getenv("PORT", "8000"))
        
        # Configurable Rate Limiting
        self.assistant_limit = int(os.getenv("PRICEPILOT_AI_ASSISTANT_LIMIT", "60"))
        self.recommendation_limit = int(os.getenv("PRICEPILOT_AI_RECOMMENDATION_LIMIT", "60"))
        self.rate_limit_enabled = os.getenv("PRICEPILOT_AI_RATE_LIMIT_ENABLED", "true").lower() == "true"

settings = Settings()
