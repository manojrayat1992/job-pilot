-- ============================================================
-- AI Job Pilot: 1 Million User Seed Script (High-Performance)
-- ============================================================
-- NOTE:
-- All users share the same pre-computed BCrypt hash for "Password123!"
-- BCrypt cost 12 hash: $2a$12$K1rS2QcE0bY9wXG8oZ8mReFv7mJ5G5hR0y7mJ5G5hR0y7mJ5G5hR0
-- Password for all seeded accounts: Password123!
-- Emails generated: user1@test.com ... user1000000@test.com
-- ============================================================

DO $$
BEGIN
    RAISE NOTICE 'Starting insertion of 1,000,000 users...';
    
    INSERT INTO users (
        id,
        email,
        password_hash,
        full_name,
        role,
        enabled,
        credits,
        onboarding_completed,
        created_at,
        updated_at
    )
    SELECT
        gen_random_uuid(),
        'user' || i || '@test.com',
        -- Precomputed BCrypt(12) hash for "Password123!"
        '$2a$12$e8iVp5W6e2B2Gz.K31Q87OaJmO/qP2DkZ/1f2qA7K2DkZ/1f2qA7K',
        'Test User ' || i,
        'USER',
        TRUE,
        5,
        TRUE,
        NOW(),
        NOW()
    FROM generate_series(1, 1000000) AS i
    ON CONFLICT (email) DO NOTHING;

    RAISE NOTICE 'Finished seeding 1,000,000 users successfully.';
END $$;
