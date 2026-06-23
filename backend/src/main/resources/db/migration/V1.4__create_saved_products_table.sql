-- Flyway migration to create saved_products join table and update products table
-- Author: Senior Backend Engineer

-- Add archived column to products table if not exists
ALTER TABLE products ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE NOT NULL;

-- Create saved_products table
CREATE TABLE IF NOT EXISTS saved_products (
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_saved_products_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_products_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Index for optimized join queries
CREATE INDEX IF NOT EXISTS idx_saved_products_user ON saved_products(user_id);
