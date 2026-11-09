-- Add guest session support to carts for the storefront surface.
ALTER TABLE carts ADD COLUMN owner_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE carts ADD COLUMN guest_session_hash VARCHAR(64);

-- Allow NULL customer_id for guest carts.
ALTER TABLE carts ALTER COLUMN customer_id DROP NOT NULL;

-- One active cart per guest session hash per tenant.
CREATE UNIQUE INDEX uk_carts_active_guest
    ON carts (tenant_id, guest_session_hash)
    WHERE status = 'ACTIVE' AND owner_type = 'GUEST';

-- Update existing rows to CUSTOMER owner type.
UPDATE carts SET owner_type = 'CUSTOMER' WHERE owner_type IS NULL;
