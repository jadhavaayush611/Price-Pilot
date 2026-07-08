import os

class Settings:
    def __init__(self):
        self.api_key = os.getenv("PRICEPILOT_AI_API_KEY", "pricepilot-secret-api-key")
        
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
        self.env = os.getenv("ENV", "production")

settings = Settings()
