-- A READY asset can have its transient staging object removed by MediaCleanupJob.
-- The final object_key remains the authoritative public object reference.
ALTER TABLE media_assets
    ALTER COLUMN upload_key DROP NOT NULL;
