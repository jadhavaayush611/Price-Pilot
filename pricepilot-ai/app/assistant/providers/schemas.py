from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field

class BuyConfidenceSchema(BaseModel):
    score: int = Field(ge=0, le=100, description="Buy confidence score percentage from 0 to 100")
    reason: str = Field(min_length=1, description="Data-driven reason grounded in historical pricing")

class ProductComparisonItem(BaseModel):
    id: str
    name: str
    price: float
    discount: float = 0.0
    sellersCount: int = 0
    score: float = 0.0
    trend: str = "STABLE"
    analytics: Dict[str, Any] = Field(default_factory=dict)

class ProductComparisonSchema(BaseModel):
    products: List[ProductComparisonItem] = Field(default_factory=list)
    summary: str = Field(default="", description="Summary comparison of products")
