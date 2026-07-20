from pydantic import BaseModel, Field, ConfigDict
from typing import List, Dict, Any, Optional

class ProductFeature(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str = Field(..., alias="productId", description="Unique product identifier")
    category: str
    brand: Optional[str] = "Unknown"
    currentMinPrice: Optional[float] = 0.0
    originalMinPrice: Optional[float] = 0.0
    averageSellerRating: Optional[float] = 0.0
    viewCount: Optional[float] = 0.0
    saveCount: Optional[float] = 0.0
    watchlistCount: Optional[float] = 0.0
    trendingScore: Optional[float] = 0.0
    discountPercentage: Optional[float] = 0.0

class UserProfile(BaseModel):
    preferredCategories: Dict[str, float] = Field(default_factory=dict)
    preferredBrands: Dict[str, float] = Field(default_factory=dict)
    preferredSellers: Dict[str, float] = Field(default_factory=dict)
    minPrice: Optional[float] = None
    maxPrice: Optional[float] = None

class UserInteraction(BaseModel):
    productId: str
    interactionType: str
    createdAt: Optional[str] = None

class PredictRequest(BaseModel):
    userId: str = Field(..., description="UUID or identifier of user requesting recommendation")
    algorithm: str = Field("Hybrid", description="Recommendation algorithm (Popularity, Content, Collaborative, Hybrid)")
    limit: int = Field(10, ge=1, le=100)
    candidates: List[ProductFeature] = Field(..., description="List of candidate products to score")
    userProfile: Optional[UserProfile] = None
    interactions: Optional[List[UserInteraction]] = None

class ScoredRecommendation(BaseModel):
    productId: str
    score: float
    reasons: List[str]

class PredictResponse(BaseModel):
    algorithm: str
    score: float
    recommendations: List[ScoredRecommendation]

class SimilarRequest(BaseModel):
    targetProductId: str = Field(..., description="Product ID of target product")
    targetProduct: ProductFeature = Field(..., description="Target product features")
    candidates: List[ProductFeature] = Field(..., description="List of candidate products to score")
    limit: int = Field(10, ge=1, le=100)

class SimilarResponse(BaseModel):
    targetProductId: str
    similarProducts: List[ScoredRecommendation]
