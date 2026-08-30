-- Flyway migration to create tables for smart watchlists & price alerts
-- Author: Principal Intelligence & Notification Systems Engineer

CREATE TABLE IF NOT EXISTS watchlist_alert_preferences (
    id UUID PRIMARY KEY,
    watchlist_id UUID NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    price_drop_enabled BOOLEAN NOT NULL DEFAULT true,
    price_drop_percentage NUMERIC(5, 2) NOT NULL DEFAULT 10.00,
    target_price_enabled BOOLEAN NOT NULL DEFAULT true,
    historical_low_enabled BOOLEAN NOT NULL DEFAULT true,
    good_deal_enabled BOOLEAN NOT NULL DEFAULT true,
    back_in_stock_enabled BOOLEAN NOT NULL DEFAULT true,
    price_increase_enabled BOOLEAN NOT NULL DEFAULT false,
    last_notified_price NUMERIC(10, 2),
    last_notified_target_price NUMERIC(10, 2),
    last_notified_historical_low NUMERIC(10, 2),
    last_notified_deal_quality VARCHAR(50),
    last_notified_in_stock BOOLEAN,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_watchlist_alert_prefs_watchlist FOREIGN KEY (watchlist_id) REFERENCES price_watchlists(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_watchlist_alert_prefs_watchlist ON watchlist_alert_preferences(watchlist_id);

CREATE TABLE IF NOT EXISTS price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    watchlist_id UUID,
    alert_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    trigger_value NUMERIC(10, 2),
    observed_value NUMERIC(10, 2),
    deduplication_key VARCHAR(255) NOT NULL UNIQUE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_price_alerts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_alerts_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_alerts_watchlist FOREIGN KEY (watchlist_id) REFERENCES price_watchlists(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_price_alerts_user_created ON price_alerts(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user_read ON price_alerts(user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user_product ON price_alerts(user_id, product_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_dedup ON price_alerts(deduplication_key);
