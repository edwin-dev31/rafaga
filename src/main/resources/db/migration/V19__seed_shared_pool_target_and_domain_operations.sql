INSERT INTO deployment_targets (
    id,
    target_type,
    protocol,
    private_origin,
    health_path,
    target_status,
    capacity_state,
    created_at,
    updated_at
)
VALUES (
    'shared-pool',
    'SHARED_POOL',
    'http',
    'http://backend:8080',
    '/actuator/health/readiness',
    'HEALTHY',
    'AVAILABLE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

UPDATE tenant_domains
SET target_id = 'shared-pool'
WHERE target_id IS NULL;

UPDATE tenant_domains
SET directory_version = 1
WHERE domain_status = 'ACTIVE'
  AND directory_version = 0;

UPDATE tenant_directory_state
SET current_version = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE singleton_id = 1
  AND current_version = 0
  AND EXISTS (SELECT 1 FROM tenant_domains WHERE directory_version = 1);

ALTER TABLE tenant_domain_operations
    ADD COLUMN IF NOT EXISTS directory_version BIGINT;
