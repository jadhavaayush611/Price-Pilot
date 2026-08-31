-- Flyway migration to create tables for user shopping preferences
-- Author: Principal Personalization & Shopping Intelligence Engineer

CREATE TABLE IF NOT EXISTS user_shopping_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    min_budget NUMERIC(10, 2),
    max_budget NUMERIC(10, 2),
    min_rating NUMERIC(3, 2),
    deal_sensitivity VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    price_sensitivity VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    availability_preference VARCHAR(30) NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_shopping_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_shopping_prefs_user ON user_shopping_preferences(user_id);

CREATE TABLE IF NOT EXISTS user_preferred_categories (
    preference_id UUID NOT NULL,
    category VARCHAR(100) NOT NULL,
    PRIMARY KEY (preference_id, category),
    CONSTRAINT fk_pref_categories_pref FOREIGN KEY (preference_id) REFERENCES user_shopping_preferences(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pref_categories_pref_id ON user_preferred_categories(preference_id);

CREATE TABLE IF NOT EXISTS user_preferred_brands (
    preference_id UUID NOT NULL,
    brand VARCHAR(100) NOT NULL,
    PRIMARY KEY (preference_id, brand),
    CONSTRAINT fk_pref_brands_pref FOREIGN KEY (preference_id) REFERENCES user_shopping_preferences(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pref_brands_pref_id ON user_preferred_brands(preference_id);
