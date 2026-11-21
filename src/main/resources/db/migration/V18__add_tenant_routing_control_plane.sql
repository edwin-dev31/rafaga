CREATE TABLE deployment_targets (
    id VARCHAR(100) PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    private_origin VARCHAR(512) NOT NULL,
    health_path VARCHAR(255) NOT NULL,
    target_status VARCHAR(32) NOT NULL,
    capacity_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_deployment_targets_protocol CHECK (protocol IN ('http', 'https'))
);

CREATE INDEX idx_deployment_targets_routable
    ON deployment_targets (target_status, target_type);

ALTER TABLE tenant_domains
    ADD COLUMN domain_status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    ADD COLUMN frontend_origin VARCHAR(512),
    ADD COLUMN target_id VARCHAR(100) REFERENCES deployment_targets (id),
    ADD COLUMN directory_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN suspended_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN operation_state VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    ADD COLUMN operation_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Preserve already-serving stores. Domains added after this migration begin in
-- REQUESTED and require an explicit lifecycle transition before gateway use.
UPDATE tenant_domains
SET domain_status = 'ACTIVE',
    activated_at = requested_at,
    operation_state = 'SUCCEEDED'
WHERE domain_status = 'REQUESTED';

CREATE INDEX idx_tenant_domains_directory
    ON tenant_domains (domain_status, directory_version);

CREATE INDEX idx_tenant_domains_target
    ON tenant_domains (target_id)
    WHERE target_id IS NOT NULL;

CREATE TABLE tenant_directory_state (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    current_version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO tenant_directory_state (singleton_id, current_version, updated_at)
VALUES (1, 0, CURRENT_TIMESTAMP);

CREATE TABLE tenant_domain_operations (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    domain VARCHAR(253) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    operation_state VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(100),
    CONSTRAINT uk_tenant_domain_operations_idempotency UNIQUE (tenant_id, domain, operation_type, idempotency_key)
);

CREATE INDEX idx_tenant_domain_operations_pending
    ON tenant_domain_operations (operation_state, requested_at);
