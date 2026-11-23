CREATE TABLE tenant_plan_limits (
    tenant_id VARCHAR(36) PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    plan_id VARCHAR(100) NOT NULL,
    max_products INTEGER NOT NULL CHECK (max_products >= 0),
    max_assets BIGINT NOT NULL CHECK (max_assets >= 0),
    max_storage_bytes BIGINT NOT NULL CHECK (max_storage_bytes >= 0),
    max_admin_users INTEGER NOT NULL CHECK (max_admin_users >= 0),
    max_domains INTEGER NOT NULL CHECK (max_domains >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO tenant_plan_limits (
    tenant_id,
    plan_id,
    max_products,
    max_assets,
    max_storage_bytes,
    max_admin_users,
    max_domains,
    updated_at
)
SELECT
    t.id,
    t.plan_id,
    CASE LOWER(t.plan_id)
        WHEN 'pro' THEN 500
        WHEN 'professional' THEN 500
        WHEN 'enterprise' THEN 1000
        ELSE 100
    END,
    CASE LOWER(t.plan_id)
        WHEN 'pro' THEN 2000
        WHEN 'professional' THEN 2000
        WHEN 'enterprise' THEN 4000
        ELSE 500
    END,
    CASE LOWER(t.plan_id)
        WHEN 'pro' THEN 5368709120
        WHEN 'professional' THEN 5368709120
        WHEN 'enterprise' THEN 21474836480
        ELSE 1073741824
    END,
    CASE LOWER(t.plan_id)
        WHEN 'pro' THEN 3
        WHEN 'professional' THEN 3
        WHEN 'enterprise' THEN 5
        ELSE 1
    END,
    CASE LOWER(t.plan_id)
        WHEN 'pro' THEN 2
        WHEN 'professional' THEN 2
        WHEN 'enterprise' THEN 4
        ELSE 1
    END,
    NOW()
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;
