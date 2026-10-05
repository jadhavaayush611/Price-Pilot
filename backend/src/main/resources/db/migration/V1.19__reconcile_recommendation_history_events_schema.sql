-- Migration: Reconcile recommendation_history_events schema column types with JPA Double mapping
-- Description: Alters score and confidence columns from NUMERIC to DOUBLE PRECISION with explicit USING casts and range CHECK constraints

ALTER TABLE recommendation_history_events
    ALTER COLUMN score TYPE DOUBLE PRECISION USING score::DOUBLE PRECISION,
    ALTER COLUMN confidence TYPE DOUBLE PRECISION USING confidence::DOUBLE PRECISION;

ALTER TABLE recommendation_history_events
    ADD CONSTRAINT chk_rec_history_score_range CHECK (score >= 0.0 AND score <= 100.0),
    ADD CONSTRAINT chk_rec_history_confidence_range CHECK (confidence >= 0.0 AND confidence <= 1.0);
