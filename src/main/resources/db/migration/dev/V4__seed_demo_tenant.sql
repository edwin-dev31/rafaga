INSERT INTO tenants (
    id,
    name,
    deployment_mode,
    database_config_id,
    plan_id,
    status,
    created_at,
    updated_at
)
VALUES (
    'tenant-demo',
    'Rafaga Demo Store',
    'SHARED_DB',
    NULL,
    'starter',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_domains (tenant_id, domain)
VALUES ('tenant-demo', 'demo.rafaga.test')
ON CONFLICT (tenant_id, domain) DO NOTHING;

INSERT INTO tenant_feature_flags (id, tenant_id, feature_name, enabled, effective_from, effective_until)
VALUES
    ('feature-demo-catalog', 'tenant-demo', 'catalog', TRUE, CURRENT_TIMESTAMP, NULL),
    ('feature-demo-inventory', 'tenant-demo', 'inventory', TRUE, CURRENT_TIMESTAMP, NULL),
    ('feature-demo-orders', 'tenant-demo', 'orders', TRUE, CURRENT_TIMESTAMP, NULL),
    ('feature-demo-analytics', 'tenant-demo', 'analytics', FALSE, CURRENT_TIMESTAMP, NULL)
ON CONFLICT (tenant_id, feature_name) DO NOTHING;
