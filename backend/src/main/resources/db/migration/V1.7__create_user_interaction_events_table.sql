-- Flyway migration to create user_interaction_events table
-- Author: Principal Backend Engineer & Data Platform Architect

CREATE TABLE IF NOT EXISTS user_interaction_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    product_id UUID,
    seller_id UUID,
    interaction_type VARCHAR(50) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_interaction_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_interaction_events_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_interaction_events_seller FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL
);

-- Indexes for performance & query optimizations (avoiding full table scans)
CREATE INDEX IF NOT EXISTS idx_user_interaction_events_user_id ON user_interaction_events(user_id);
CREATE INDEX IF NOT EXISTS idx_user_interaction_events_product_id ON user_interaction_events(product_id);
CREATE INDEX IF NOT EXISTS idx_user_interaction_events_seller_id ON user_interaction_events(seller_id);
CREATE INDEX IF NOT EXISTS idx_user_interaction_events_type_created ON user_interaction_events(interaction_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_interaction_events_created_at ON user_interaction_events(created_at DESC);
