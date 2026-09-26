-- Flyway migration to add currency column to user_shopping_preferences
-- Author: Principal Currency & Shopping Intelligence Engineer

ALTER TABLE user_shopping_preferences
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'INR';
