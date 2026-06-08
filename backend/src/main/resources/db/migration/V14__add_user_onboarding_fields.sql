-- Add onboarding fields to users table
ALTER TABLE users ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN work_mode VARCHAR(50);
ALTER TABLE users ADD COLUMN target_role VARCHAR(100);
ALTER TABLE users ADD COLUMN current_org VARCHAR(255);
ALTER TABLE users ADD COLUMN target_salary VARCHAR(100);
