import logging
import sys
from pricepilot import PricePilotClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger("demo_dashboard")

def run():
    client = PricePilotClient(base_url="http://localhost:8080/api/v1")
    
    # Dashboard requires auth
    email = "sdk_user_test@example.com"
    password = "password123"
    
    logger.info("Logging in to fetch dashboard data...")
    try:
        client.auth.login(email=email, password=password)
    except Exception as e:
        logger.error(f"Login failed (run demo_auth.py first to create the account): {e}")
        logger.error("Please ensure database contains the user and backend is running.")
        sys.exit(1)
        
    try:
        logger.info("Fetching dashboard data...")
        dashboard = client.dashboard.get_dashboard_data()
        
        logger.info(f"Dashboard retrieved successfully for: {dashboard.first_name} {dashboard.last_name} ({dashboard.email})")
        logger.info(f"Summary Statistics:")
        logger.info(f"  - Saved Products Count: {dashboard.saved_count}")
        logger.info(f"  - Watchlisted Products Count: {dashboard.watchlist_count}")
        logger.info(f"  - Active Price Alerts Count: {dashboard.active_price_alerts_count}")
        logger.info(f"  - Total Activities Count: {dashboard.total_activities_count}")
        
        logger.info("Recent searches:")
        for search in dashboard.recent_searches[:5]:
            logger.info(f"  - {search}")
            
        logger.info("Recommendations preview:")
        for rec in dashboard.recommendations[:3]:
            logger.info(f"  - {rec.name} (Brand: {rec.brand})")
            
        logger.info("Watchlists preview:")
        for watchlist in dashboard.watchlists[:3]:
            logger.info(f"  - Product: {watchlist.product_name} | Target Price: ${watchlist.target_price} (Current: ${watchlist.current_best_price})")
            
    except Exception as e:
        logger.error(f"Failed to fetch dashboard data: {e}")

if __name__ == "__main__":
    run()
