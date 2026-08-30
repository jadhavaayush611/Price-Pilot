-- Flyway migration to add composite index on price_histories for optimized chronological scans
-- Author: Principal Database & Performance Engineer

CREATE INDEX IF NOT EXISTS idx_price_histories_product_changed_at 
ON price_histories(product_id, changed_at ASC);
