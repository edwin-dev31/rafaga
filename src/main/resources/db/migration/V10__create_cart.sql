CREATE TABLE carts (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    customer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_out_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_carts_tenant_id UNIQUE (tenant_id, id)
);

-- Only one ACTIVE cart is allowed per customer per tenant.
-- A partial unique index enforces this without affecting CHECKED_OUT / ABANDONED carts.
CREATE UNIQUE INDEX uk_carts_active_customer
    ON carts (tenant_id, customer_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_carts_tenant_id ON carts (tenant_id);
CREATE INDEX idx_carts_tenant_customer ON carts (tenant_id, customer_id);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    cart_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    line_total NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (tenant_id, cart_id)
        REFERENCES carts (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id),
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (tenant_id, product_id, variant_id)
        REFERENCES product_variants (tenant_id, product_id, id),
    CONSTRAINT ck_cart_items_amounts CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
);

CREATE INDEX idx_cart_items_tenant_id ON cart_items (tenant_id);
CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);
-- One item per (cart, product, variant) — NULL variant_id treated as a distinct single value.
CREATE UNIQUE INDEX uk_cart_items_cart_product_variant
    ON cart_items (cart_id, product_id, variant_id) NULLS NOT DISTINCT;

SELECT enforce_tenant_row_level_security('carts');
SELECT enforce_tenant_row_level_security('cart_items');
