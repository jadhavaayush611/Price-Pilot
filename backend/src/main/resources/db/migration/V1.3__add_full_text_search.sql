-- Flyway migration to enable PostgreSQL Full-Text Search
-- Add search_vector column to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Update existing products with generated search_vector
UPDATE products
SET search_vector = to_tsvector('english', 
    coalesce(name, '') || ' ' || 
    coalesce(brand, '') || ' ' || 
    coalesce(category, '') || ' ' || 
    coalesce(description, '')
);

-- Create a GIN index on search_vector
CREATE INDEX IF NOT EXISTS idx_products_search_vector ON products USING GIN(search_vector);

-- Function to match search text
CREATE OR REPLACE FUNCTION fts_match(vector tsvector, query text)
RETURNS boolean AS $$
    SELECT vector @@ websearch_to_tsquery('english', query);
$$ LANGUAGE sql IMMUTABLE;

-- Function to get search rank
CREATE OR REPLACE FUNCTION fts_rank(vector tsvector, query text)
RETURNS real AS $$
    SELECT ts_rank(vector, websearch_to_tsquery('english', query));
$$ LANGUAGE sql IMMUTABLE;

-- Trigger function to update search_vector automatically on inserts and updates
CREATE OR REPLACE FUNCTION products_search_vector_trigger()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', 
        coalesce(NEW.name, '') || ' ' || 
        coalesce(NEW.brand, '') || ' ' || 
        coalesce(NEW.category, '') || ' ' || 
        coalesce(NEW.description, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop trigger if it exists and recreate it
DROP TRIGGER IF EXISTS trg_products_search_vector_update ON products;
CREATE TRIGGER trg_products_search_vector_update
BEFORE INSERT OR UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION products_search_vector_trigger();

-- Composite index for keyset pagination
CREATE INDEX IF NOT EXISTS idx_products_created_id ON products(created_at DESC, id DESC);
