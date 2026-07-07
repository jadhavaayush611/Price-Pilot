from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Dict, Any, Optional
from decimal import Decimal

def _parse_date(val: Any) -> Optional[datetime]:
    if not val:
        return None
    if isinstance(val, datetime):
        return val
    try:
        s = str(val)
        if s.endswith('Z'):
            s = s[:-1] + '+00:00'
        return datetime.fromisoformat(s)
    except ValueError:
        return None

def _parse_decimal(val: Any) -> Optional[Decimal]:
    if val is None:
        return None
    try:
        return Decimal(str(val))
    except (ValueError, TypeError):
        return None

@dataclass
class UserResponse:
    id: str
    email: str
    first_name: str
    last_name: str
    role: str
    enabled: bool

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "UserResponse":
        return cls(
            id=data["id"],
            email=data["email"],
            first_name=data["firstName"],
            last_name=data["lastName"],
            role=data["role"],
            enabled=data.get("enabled", True)
        )

@dataclass
class AuthResponse:
    token: str
    user: UserResponse

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "AuthResponse":
        return cls(
            token=data["token"],
            user=UserResponse.from_dict(data["user"])
        )

@dataclass
class SellerResponse:
    id: str
    name: str
    website_url: str
    logo_url: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "SellerResponse":
        return cls(
            id=data["id"],
            name=data["name"],
            website_url=data["websiteUrl"],
            logo_url=data.get("logoUrl"),
            created_at=_parse_date(data.get("createdAt")),
            updated_at=_parse_date(data.get("updatedAt"))
        )

@dataclass
class ProductPriceResponse:
    id: str
    current_price: Decimal
    original_price: Decimal
    discount_percentage: Decimal
    product_url: str
    last_updated: datetime
    seller: SellerResponse
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProductPriceResponse":
        return cls(
            id=data["id"],
            current_price=_parse_decimal(data["currentPrice"]),
            original_price=_parse_decimal(data["originalPrice"]),
            discount_percentage=_parse_decimal(data["discountPercentage"]),
            product_url=data["productUrl"],
            last_updated=_parse_date(data["lastUpdated"]),
            seller=SellerResponse.from_dict(data["seller"]),
            created_at=_parse_date(data.get("createdAt")),
            updated_at=_parse_date(data.get("updatedAt"))
        )

@dataclass
class ProductResponse:
    id: str
    name: str
    brand: Optional[str]
    category: str
    description: Optional[str]
    image_url: Optional[str]
    archived: bool
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
    prices: List[ProductPriceResponse] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProductResponse":
        prices_data = data.get("prices") or []
        prices = [ProductPriceResponse.from_dict(p) for p in prices_data]
        return cls(
            id=data["id"],
            name=data["name"],
            brand=data.get("brand"),
            category=data["category"],
            description=data.get("description"),
            image_url=data.get("imageUrl"),
            archived=data.get("archived", False),
            created_at=_parse_date(data.get("createdAt")),
            updated_at=_parse_date(data.get("updatedAt")),
            prices=prices
        )

@dataclass
class ProductPriceSearchResult:
    id: str
    current_price: Decimal
    original_price: Decimal
    discount_percentage: Decimal
    product_url: str
    last_updated: datetime
    seller: SellerResponse

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProductPriceSearchResult":
        return cls(
            id=data["id"],
            current_price=_parse_decimal(data["currentPrice"]),
            original_price=_parse_decimal(data["originalPrice"]),
            discount_percentage=_parse_decimal(data["discountPercentage"]),
            product_url=data["productUrl"],
            last_updated=_parse_date(data["lastUpdated"]),
            seller=SellerResponse.from_dict(data["seller"])
        )

@dataclass
class ProductSearchResult:
    id: str
    name: str
    brand: Optional[str]
    category: str
    description: Optional[str]
    image_url: Optional[str]
    archived: bool
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
    prices: List[ProductPriceSearchResult] = field(default_factory=list)
    lowest_price: Optional[Decimal] = None
    highest_price: Optional[Decimal] = None

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProductSearchResult":
        prices_data = data.get("prices") or []
        prices = [ProductPriceSearchResult.from_dict(p) for p in prices_data]
        return cls(
            id=data["id"],
            name=data["name"],
            brand=data.get("brand"),
            category=data["category"],
            description=data.get("description"),
            image_url=data.get("imageUrl"),
            archived=data.get("archived", False),
            created_at=_parse_date(data.get("createdAt")),
            updated_at=_parse_date(data.get("updatedAt")),
            prices=prices,
            lowest_price=_parse_decimal(data.get("lowestPrice")),
            highest_price=_parse_decimal(data.get("highestPrice"))
        )

@dataclass
class PageResponse:
    content: List[Any]
    number: int
    size: int
    total_elements: int
    total_pages: int
    last: bool
    first: bool
    empty: bool

    @classmethod
    def from_dict(cls, data: Dict[str, Any], item_cls: type) -> "PageResponse":
        raw_content = data.get("content") or []
        content = [item_cls.from_dict(x) for x in raw_content]
        return cls(
            content=content,
            number=data["number"],
            size=data["size"],
            total_elements=data["totalElements"],
            total_pages=data["totalPages"],
            last=data.get("last", False),
            first=data.get("first", False),
            empty=data.get("empty", False)
        )

@dataclass
class KeysetPageResponse:
    content: List[Any]
    next_cursor: Optional[str]
    prev_cursor: Optional[str]
    has_more: bool

    @classmethod
    def from_dict(cls, data: Dict[str, Any], item_cls: type) -> "KeysetPageResponse":
        raw_content = data.get("content") or []
        content = [item_cls.from_dict(x) for x in raw_content]
        return cls(
            content=content,
            next_cursor=data.get("nextCursor"),
            prev_cursor=data.get("prevCursor"),
            has_more=data.get("hasMore", False)
        )

@dataclass
class SavedProductResponse:
    product_id: str
    name: str
    brand: Optional[str]
    category: str
    image_url: Optional[str]
    best_price: Optional[Decimal]
    saved_at: datetime

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "SavedProductResponse":
        return cls(
            product_id=data["productId"],
            name=data["name"],
            brand=data.get("brand"),
            category=data["category"],
            image_url=data.get("imageUrl"),
            best_price=_parse_decimal(data.get("bestPrice")),
            saved_at=_parse_date(data["savedAt"])
        )

@dataclass
class WatchlistResponse:
    id: str
    product_id: str
    product_name: str
    brand: Optional[str]
    image_url: Optional[str]
    target_price: Decimal
    current_best_price: Optional[Decimal]
    price_difference: Optional[Decimal]
    active: bool
    created_at: datetime
    updated_at: datetime

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "WatchlistResponse":
        return cls(
            id=data["id"],
            product_id=data["productId"],
            product_name=data["productName"],
            brand=data.get("brand"),
            image_url=data.get("imageUrl"),
            target_price=_parse_decimal(data["targetPrice"]),
            current_best_price=_parse_decimal(data.get("currentBestPrice")),
            price_difference=_parse_decimal(data.get("priceDifference")),
            active=data.get("active", True),
            created_at=_parse_date(data.get("createdAt")),
            updated_at=_parse_date(data.get("updatedAt"))
        )

@dataclass
class UserInteractionEventResponse:
    id: str
    user_id: str
    user_email: str
    product_id: Optional[str]
    product_name: Optional[str]
    seller_id: Optional[str]
    seller_name: Optional[str]
    interaction_type: str
    metadata: Dict[str, Any]
    created_at: datetime

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "UserInteractionEventResponse":
        return cls(
            id=data["id"],
            user_id=data["userId"],
            user_email=data["userEmail"],
            product_id=data.get("productId"),
            product_name=data.get("productName"),
            seller_id=data.get("sellerId"),
            seller_name=data.get("sellerName"),
            interaction_type=data["interactionType"],
            metadata=data.get("metadata") or {},
            created_at=_parse_date(data["createdAt"])
        )

@dataclass
class ProductAnalyticsResponse:
    product_id: str
    view_count: int
    save_count: int
    watchlist_count: int
    price_change_count: int
    trending_score: float

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProductAnalyticsResponse":
        return cls(
            product_id=data["productId"],
            view_count=data["viewCount"],
            save_count=data["saveCount"],
            watchlist_count=data["watchlistCount"],
            price_change_count=data["priceChangeCount"],
            trending_score=data.get("trendingScore", 0.0)
        )

@dataclass
class DashboardData:
    first_name: str
    last_name: str
    email: str
    role: str
    saved_count: int
    watchlist_count: int
    total_activities_count: int
    active_price_alerts_count: int
    recommendations: List[ProductResponse]
    recently_viewed: List[ProductResponse]
    price_drop_alerts: List[WatchlistResponse]
    trending_products: List[ProductResponse]
    watchlists: List[WatchlistResponse]
    saved_products: List[SavedProductResponse]
    recent_activity: List[UserInteractionEventResponse]
    recent_searches: List[str]
    most_clicked_sellers: List[Dict[str, Any]]

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "DashboardData":
        return cls(
            first_name=data.get("firstName", ""),
            last_name=data.get("lastName", ""),
            email=data.get("email", ""),
            role=data.get("role", ""),
            saved_count=data.get("savedCount", 0),
            watchlist_count=data.get("watchlistCount", 0),
            total_activities_count=data.get("totalActivitiesCount", 0),
            active_price_alerts_count=data.get("activePriceAlertsCount", 0),
            recommendations=[ProductResponse.from_dict(p) for p in (data.get("recommendations") or [])],
            recently_viewed=[ProductResponse.from_dict(p) for p in (data.get("recentlyViewed") or [])],
            price_drop_alerts=[WatchlistResponse.from_dict(w) for w in (data.get("priceDropAlerts") or [])],
            trending_products=[ProductResponse.from_dict(p) for p in (data.get("trendingProducts") or [])],
            watchlists=[WatchlistResponse.from_dict(w) for w in (data.get("watchlists") or [])],
            saved_products=[SavedProductResponse.from_dict(s) for s in (data.get("savedProducts") or [])],
            recent_activity=[UserInteractionEventResponse.from_dict(a) for a in (data.get("recentActivity") or [])],
            recent_searches=data.get("recentSearches") or [],
            most_clicked_sellers=data.get("mostClickedSellers") or []
        )
