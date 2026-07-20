-- Flyway migration to add performance tuning indexes for PricePilot M2
-- Author: Principal Database & Performance Engineer

-- Index for active triggered price watchlists query pushdown
CREATE INDEX IF NOT EXISTS idx_price_watchlists_active_best_target 
ON price_watchlists(active, current_best_price, target_price) 
WHERE active = true;

-- Indexes for product analytics orderings (most watched, most saved, popular)
CREATE INDEX IF NOT EXISTS idx_product_analytics_watchlist_desc 
ON product_analytics(watchlist_count DESC);

CREATE INDEX IF NOT EXISTS idx_product_analytics_save_desc 
ON product_analytics(save_count DESC);

CREATE INDEX IF NOT EXISTS idx_product_analytics_view_desc 
ON product_analytics(view_count DESC);

-- Composite index for product recommendation candidate filtering by category & brand
CREATE INDEX IF NOT EXISTS idx_products_category_brand 
ON products(category, brand);

-- Partial index for active (non-archived) products lookup
CREATE INDEX IF NOT EXISTS idx_products_active 
ON products(archived) 
WHERE archived = false;
