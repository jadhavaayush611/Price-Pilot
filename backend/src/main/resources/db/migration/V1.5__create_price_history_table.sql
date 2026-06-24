-- Flyway migration to create price_histories table
-- Author: Senior Backend Engineer & Data Platform Architect

CREATE TABLE IF NOT EXISTS price_histories (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    old_price DECIMAL(10, 2) NOT NULL,
    new_price DECIMAL(10, 2) NOT NULL,
    price_difference DECIMAL(10, 2) NOT NULL,
    change_percentage DECIMAL(5, 2) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_price_histories_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_histories_seller FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE
);

-- Indexes for optimized queries and sorting
CREATE INDEX IF NOT EXISTS idx_price_histories_product_id ON price_histories(product_id);
CREATE INDEX IF NOT EXISTS idx_price_histories_seller_id ON price_histories(seller_id);
CREATE INDEX IF NOT EXISTS idx_price_histories_changed_at ON price_histories(changed_at DESC);
