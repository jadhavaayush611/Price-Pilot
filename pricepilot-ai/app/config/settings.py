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

        # LLM Provider Configuration
        self.ai_provider = os.getenv("AI_PROVIDER", "gemini").lower()
        self.gemini_api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        self.gemini_primary_model = os.getenv("GEMINI_PRIMARY_MODEL") or os.getenv("GEMINI_MODEL") or "gemini-3.8-flash"
        self.gemini_fallback_model = os.getenv("GEMINI_FALLBACK_MODEL", "gemini-2.5-flash")
        self.llm_timeout_seconds = float(os.getenv("LLM_TIMEOUT_SECONDS", "3.5"))
        self.llm_temperature = float(os.getenv("LLM_TEMPERATURE", "0.2"))
        self.llm_top_p = float(os.getenv("LLM_TOP_P", "0.8"))
        self.llm_max_tokens = int(os.getenv("LLM_MAX_TOKENS", "2048"))

settings = Settings()
