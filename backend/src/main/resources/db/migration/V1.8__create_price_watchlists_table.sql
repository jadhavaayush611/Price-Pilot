-- Flyway migration to create price_watchlists table
-- Author: Principal Database Engineer

CREATE TABLE IF NOT EXISTS price_watchlists (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    target_price DECIMAL(10, 2) NOT NULL,
    current_best_price DECIMAL(10, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_price_watchlists_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_watchlists_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uc_price_watchlists_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_price_watchlists_user_id ON price_watchlists(user_id);
CREATE INDEX IF NOT EXISTS idx_price_watchlists_product_id ON price_watchlists(product_id);
