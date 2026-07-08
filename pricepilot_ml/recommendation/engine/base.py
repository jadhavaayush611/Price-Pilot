from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
import pandas as pd

class BaseRecommendationEngine(ABC):
    """Abstract base class for all recommendation engines in Python."""
    
    @abstractmethod
    def recommend(
        self,
        user_id: str,
        df_products: pd.DataFrame,
        limit: int = 10,
        df_interactions: Optional[pd.DataFrame] = None,
        **kwargs
    ) -> List[Dict[str, Any]]:
        """Generates recommendations for a specific user.
        
        Returns a list of dicts, each representing a recommendation:
        {
            "productId": str,
            "score": float,
            "algorithm": str,
            "reasons": List[str]
        }
        """
        pass

    @abstractmethod
    def get_algorithm_name(self) -> str:
        pass
