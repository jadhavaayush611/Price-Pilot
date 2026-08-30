-- Migration: Create Recommendation History Events Table for PricePilot v1.1 Phase 3
-- Description: Stores execution outputs and metadata of explainable recommendation engine v2

CREATE TABLE IF NOT EXISTS recommendation_history_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(100),
    target_product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    recommended_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    product_ids TEXT NOT NULL,
    recommendation_type VARCHAR(50) NOT NULL,
    scoring_strategy VARCHAR(100) NOT NULL,
    explanation_strategy VARCHAR(100) NOT NULL,
    score NUMERIC(6,2) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recommendation_history_user_id ON recommendation_history_events(user_id);
CREATE INDEX IF NOT EXISTS idx_recommendation_history_product_id ON recommendation_history_events(recommended_product_id);
CREATE INDEX IF NOT EXISTS idx_recommendation_history_created_at ON recommendation_history_events(created_at);
