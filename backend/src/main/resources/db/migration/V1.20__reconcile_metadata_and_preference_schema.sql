-- Migration: Reconcile recommendation_metadata and user_shopping_preferences schema types with JPA Double mappings
-- Description: Alters confidence_score and min_rating to DOUBLE PRECISION with explicit USING casts and range CHECK constraints

-- 1. Reconcile recommendation_metadata.confidence_score
ALTER TABLE recommendation_metadata
    ALTER COLUMN confidence_score TYPE DOUBLE PRECISION USING confidence_score::DOUBLE PRECISION;

ALTER TABLE recommendation_metadata
    ADD CONSTRAINT chk_rec_metadata_confidence_range CHECK (confidence_score IS NULL OR (confidence_score >= 0.0 AND confidence_score <= 1.0));

-- 2. Reconcile user_shopping_preferences.min_rating
ALTER TABLE user_shopping_preferences
    ALTER COLUMN min_rating TYPE DOUBLE PRECISION USING min_rating::DOUBLE PRECISION;

ALTER TABLE user_shopping_preferences
    ADD CONSTRAINT chk_user_pref_min_rating_range CHECK (min_rating IS NULL OR (min_rating >= 0.0 AND min_rating <= 5.0));
