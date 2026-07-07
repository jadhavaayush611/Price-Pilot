import logging
import sys
from pricepilot import PricePilotClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger("demo_search")

def run():
    client = PricePilotClient(base_url="http://localhost:8080/api/v1")
    
    logger.info("Starting product search demo...")
    try:
        # Search products (doesn't require auth)
        search_result = client.products.search(keyword="Sony", size=5)
        
        logger.info(f"Search completed! Total results: {search_result.total_elements}")
        logger.info(f"Page size: {search_result.size}, Current Page: {search_result.number}")
        
        for item in search_result.content:
            logger.info(f"Product: {item.name} | Brand: {item.brand} | Category: {item.category}")
            logger.info(f"  Price range: ${item.lowest_price} - ${item.highest_price}")
            for price in item.prices:
                logger.info(f"    Seller: {price.seller.name} | Current Price: ${price.current_price} (Original: ${price.original_price}, Discount: {price.discount_percentage}%)")
                
    except Exception as e:
        logger.error(f"Error during product search: {e}")
        logger.error("Ensure the PricePilot backend is running.")

if __name__ == "__main__":
    run()
