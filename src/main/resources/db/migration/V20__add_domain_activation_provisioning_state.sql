ALTER TABLE tenant_domains
    ADD COLUMN IF NOT EXISTS external_hostname_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_failure_code VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_tenant_domains_provisioning_due
    ON tenant_domains (domain_status, next_retry_at);

CREATE TABLE IF NOT EXISTS r2_cors_revisions (
    version BIGINT PRIMARY KEY,
    origins_json TEXT NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rollback_of_version BIGINT,
    status VARCHAR(32) NOT NULL
);
