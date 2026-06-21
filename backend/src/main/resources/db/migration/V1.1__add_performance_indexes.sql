-- Flyway migration to add performance indexes for PricePilot
-- Author: Senior Backend Performance Engineer

CREATE INDEX IF NOT EXISTS idx_product_prices_product_id ON product_prices(product_id);
CREATE INDEX IF NOT EXISTS idx_product_prices_seller_id ON product_prices(seller_id);
CREATE INDEX IF NOT EXISTS idx_sellers_name ON sellers(name);
