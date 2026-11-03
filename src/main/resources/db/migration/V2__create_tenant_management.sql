CREATE TABLE tenants (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    deployment_mode VARCHAR(24) NOT NULL,
    database_config_id VARCHAR(200),
    plan_id VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tenants_status ON tenants (status);

CREATE TABLE tenant_domains (
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    domain VARCHAR(253) NOT NULL,
    PRIMARY KEY (tenant_id, domain),
    CONSTRAINT uk_tenant_domains_domain UNIQUE (domain)
);

CREATE INDEX idx_tenant_domains_domain ON tenant_domains (domain);

CREATE TABLE tenant_feature_flags (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    feature_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_until TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_tenant_feature_flags_tenant_feature UNIQUE (tenant_id, feature_name)
);

CREATE INDEX idx_tenant_feature_flags_tenant_id ON tenant_feature_flags (tenant_id);
