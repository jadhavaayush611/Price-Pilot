-- Migration: Create Shopping Intelligence Tables for PricePilot v1.1
-- Description: Session tracking, saved comparison configurations, and recommendation engine v2 metadata

CREATE TABLE IF NOT EXISTS comparison_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(100),
    product_ids TEXT,
    title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comparison_sessions_user_id ON comparison_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_comparison_sessions_token ON comparison_sessions(session_token);

CREATE TABLE IF NOT EXISTS saved_comparisons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID REFERENCES comparison_sessions(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    product_ids TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_comparisons_user_id ON saved_comparisons(user_id);

CREATE TABLE IF NOT EXISTS recommendation_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    model_version VARCHAR(50) NOT NULL,
    algorithm_type VARCHAR(50) NOT NULL,
    score_factors JSONB,
    confidence_score NUMERIC(5,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recommendation_metadata_product_id ON recommendation_metadata(product_id);
