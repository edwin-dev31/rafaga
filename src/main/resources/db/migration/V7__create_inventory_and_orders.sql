ALTER TABLE product_variants
    ADD CONSTRAINT uk_product_variants_tenant_product_id UNIQUE (tenant_id, product_id, id);

CREATE TABLE stock_levels (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    product_id UUID NOT NULL,
    variant_id UUID,
    total_quantity INTEGER NOT NULL DEFAULT 0,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 0,
    reorder_quantity INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_stock_levels_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id),
    CONSTRAINT fk_stock_levels_variant FOREIGN KEY (tenant_id, product_id, variant_id)
        REFERENCES product_variants (tenant_id, product_id, id),
    CONSTRAINT ck_stock_levels_quantities CHECK (
        total_quantity >= 0 AND available_quantity >= 0 AND reserved_quantity >= 0
        AND available_quantity + reserved_quantity = total_quantity
        AND reorder_level >= 0 AND reorder_quantity >= 0)
);

CREATE INDEX idx_stock_levels_tenant_id ON stock_levels (tenant_id);
CREATE UNIQUE INDEX uk_stock_levels_tenant_product_variant
    ON stock_levels (tenant_id, product_id, variant_id) NULLS NOT DISTINCT;

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    product_id UUID NOT NULL,
    variant_id UUID,
    type VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    previous_total INTEGER NOT NULL,
    new_total INTEGER NOT NULL,
    reference_id VARCHAR(100),
    notes VARCHAR(1000),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_inventory_movements_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id),
    CONSTRAINT fk_inventory_movements_variant FOREIGN KEY (tenant_id, product_id, variant_id)
        REFERENCES product_variants (tenant_id, product_id, id),
    CONSTRAINT ck_inventory_movements_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_movements_tenant_id ON inventory_movements (tenant_id);
CREATE INDEX idx_inventory_movements_tenant_product ON inventory_movements (tenant_id, product_id);

CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    product_id UUID NOT NULL,
    variant_id UUID,
    quantity INTEGER NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_stock_reservations_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id),
    CONSTRAINT fk_stock_reservations_variant FOREIGN KEY (tenant_id, product_id, variant_id)
        REFERENCES product_variants (tenant_id, product_id, id),
    CONSTRAINT ck_stock_reservations_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservations_tenant_id ON stock_reservations (tenant_id);
CREATE INDEX idx_stock_reservations_tenant_reference ON stock_reservations (tenant_id, reference_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    customer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_orders_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_orders_amounts CHECK (subtotal >= 0 AND total >= 0)
);

CREATE INDEX idx_orders_tenant_id ON orders (tenant_id);
CREATE INDEX idx_orders_tenant_customer ON orders (tenant_id, customer_id);

CREATE TABLE order_lines (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    line_total NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_lines_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES orders (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_order_lines_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id),
    CONSTRAINT fk_order_lines_variant FOREIGN KEY (tenant_id, product_id, variant_id)
        REFERENCES product_variants (tenant_id, product_id, id),
    CONSTRAINT ck_order_lines_amounts CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
);

CREATE INDEX idx_order_lines_tenant_id ON order_lines (tenant_id);
CREATE INDEX idx_order_lines_tenant_order ON order_lines (tenant_id, order_id);

SELECT enforce_tenant_row_level_security('stock_levels');
SELECT enforce_tenant_row_level_security('inventory_movements');
SELECT enforce_tenant_row_level_security('stock_reservations');
SELECT enforce_tenant_row_level_security('orders');
SELECT enforce_tenant_row_level_security('order_lines');
