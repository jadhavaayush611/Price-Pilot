import logging
import sys
from pricepilot import PricePilotClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger("demo_recommendations")

def run():
    client = PricePilotClient(base_url="http://localhost:8080/api/v1")
    
    # Recommendations require auth. We log in first.
    email = "sdk_user_test@example.com"
    password = "password123"
    
    logger.info("Logging in to fetch personalized recommendations...")
    try:
        client.auth.login(email=email, password=password)
    except Exception as e:
        logger.error(f"Login failed (run demo_auth.py first to create the account): {e}")
        logger.warning("Attempting to get trending/similar recommendations which do not require user-specific login...")
        
    # 1. Personalized Recommendations
    try:
        logger.info("Fetching personalized recommendations...")
        recs = client.recommendations.get_recommendations(size=5)
        logger.info(f"Retrieved {len(recs.content)} personalized recommendations.")
        for product in recs.content:
            logger.info(f" - Rec Product: {product.name} (Brand: {product.brand})")
    except Exception as e:
        logger.error(f"Failed to fetch personalized recommendations: {e}")

    # 2. Trending Products
    try:
        logger.info("Fetching trending products...")
        trending = client.recommendations.get_trending_products(limit=3)
        logger.info(f"Retrieved {len(trending)} trending products.")
        for product in trending:
            logger.info(f" - Trending Product: {product.name} (Category: {product.category})")
            
        # Get similar products for the first trending product if we have one
        if trending:
            target_id = trending[0].id
            logger.info(f"Fetching similar products for: {trending[0].name} (ID: {target_id})...")
            similar = client.recommendations.get_similar_products(product_id=target_id, limit=3)
            logger.info(f"Retrieved {len(similar)} similar products.")
            for product in similar:
                logger.info(f"   - Similar Product: {product.name} (Brand: {product.brand})")
    except Exception as e:
        logger.error(f"Failed to fetch trending/similar products: {e}")

if __name__ == "__main__":
    run()
