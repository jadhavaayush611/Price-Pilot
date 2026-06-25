-- Flyway migration to create product_analytics table
-- Author: Senior Backend Engineer & Product Analytics Architect

CREATE TABLE IF NOT EXISTS product_analytics (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    view_count BIGINT NOT NULL DEFAULT 0,
    save_count BIGINT NOT NULL DEFAULT 0,
    watchlist_count BIGINT NOT NULL DEFAULT 0,
    price_change_count BIGINT NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_analytics_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_product_analytics_view_count CHECK (view_count >= 0),
    CONSTRAINT chk_product_analytics_save_count CHECK (save_count >= 0),
    CONSTRAINT chk_product_analytics_watchlist_count CHECK (watchlist_count >= 0),
    CONSTRAINT chk_product_analytics_price_change_count CHECK (price_change_count >= 0)
);

-- Index for product lookup optimization
CREATE INDEX IF NOT EXISTS idx_product_analytics_product_id ON product_analytics(product_id);
