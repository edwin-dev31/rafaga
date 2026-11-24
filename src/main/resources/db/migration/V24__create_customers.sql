-- Tenant-scoped customer directory independent of Keycloak accounts.
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    user_id UUID,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customers_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_customers_tenant_id ON customers (tenant_id);
CREATE INDEX idx_customers_tenant_full_name ON customers (tenant_id, lower(full_name));

CREATE UNIQUE INDEX uk_customers_tenant_email
    ON customers (tenant_id, lower(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX uk_customers_tenant_phone
    ON customers (tenant_id, phone)
    WHERE phone IS NOT NULL;

CREATE UNIQUE INDEX uk_customers_tenant_user
    ON customers (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

SELECT enforce_tenant_row_level_security('customers');
