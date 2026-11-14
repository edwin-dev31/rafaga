INSERT INTO categories (id, tenant_id, name, slug, parent_id, display_order)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'tenant-demo',
    'Featured',
    'featured',
    NULL,
    0
)
ON CONFLICT (tenant_id, slug) DO NOTHING;

INSERT INTO products (
    id,
    tenant_id,
    sku,
    name,
    description,
    base_price,
    status,
    created_at,
    updated_at
)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    'tenant-demo',
    'DEMO-TEE',
    'Rafaga Demo T-Shirt',
    'Development-only sample catalog item.',
    25.00,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, sku) DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'
WHERE EXISTS (
    SELECT 1 FROM products WHERE id = '20000000-0000-0000-0000-000000000001' AND tenant_id = 'tenant-demo'
)
AND EXISTS (
    SELECT 1 FROM categories WHERE id = '10000000-0000-0000-0000-000000000001' AND tenant_id = 'tenant-demo'
)
ON CONFLICT DO NOTHING;
