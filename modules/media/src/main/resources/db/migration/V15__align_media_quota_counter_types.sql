-- Hibernate maps Java long quota counters to PostgreSQL BIGINT.
-- Keep this corrective migration separate from V14 because V14 may already
-- have been applied to shared or dedicated tenant databases.
ALTER TABLE tenant_media_quotas
    ALTER COLUMN max_assets TYPE BIGINT,
    ALTER COLUMN used_assets TYPE BIGINT,
    ALTER COLUMN reserved_assets TYPE BIGINT;
