CREATE TABLE IF NOT EXISTS tenant_target_transitions (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    domain VARCHAR(253) NOT NULL,
    from_target_id VARCHAR(100) REFERENCES deployment_targets (id),
    to_target_id VARCHAR(100) NOT NULL REFERENCES deployment_targets (id),
    transition_state VARCHAR(32) NOT NULL,
    data_validation_checksum VARCHAR(128),
    initiated_by VARCHAR(150) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_tenant_target_transitions_tenant
    ON tenant_target_transitions (tenant_id, created_at DESC);
