-- Flyway migration to add locked column to users table
-- Author: Principal Application Security Engineer

ALTER TABLE users ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
