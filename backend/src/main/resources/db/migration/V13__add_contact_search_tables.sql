-- Add credits to users table
ALTER TABLE users ADD COLUMN credits INTEGER NOT NULL DEFAULT 5;

-- Create hiring_managers table
CREATE TABLE hiring_managers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    job_title VARCHAR(255),
    company_name VARCHAR(255),
    company_domain VARCHAR(255),
    email VARCHAR(255),
    linkedin_url VARCHAR(255),
    function VARCHAR(100),
    management_level VARCHAR(100),
    country VARCHAR(100),
    is_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Table for tracking which user unlocked which contact
CREATE TABLE user_contact_unlocks (
    user_id UUID REFERENCES users(id),
    contact_id UUID REFERENCES hiring_managers(id),
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, contact_id)
);
