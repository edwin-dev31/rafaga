CREATE TABLE categories (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    parent_id UUID,
    display_order INTEGER NOT NULL,
    CONSTRAINT uk_categories_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT uk_categories_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_categories_tenant_parent FOREIGN KEY (tenant_id, parent_id)
        REFERENCES categories (tenant_id, id)
);

CREATE INDEX idx_categories_tenant_id ON categories (tenant_id);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    base_price NUMERIC(19, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT uk_products_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_products_base_price CHECK (base_price >= 0)
);

CREATE INDEX idx_products_tenant_id ON products (tenant_id);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(150) NOT NULL,
    price_adjustment NUMERIC(19, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT uk_product_variants_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT fk_product_variants_tenant_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_product_variants_tenant_id ON product_variants (tenant_id);

CREATE TABLE product_categories (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, category_id)
);

CREATE TABLE variant_attributes (
    variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (variant_id, attribute_name)
);

SELECT enforce_tenant_row_level_security('products');
SELECT enforce_tenant_row_level_security('categories');
SELECT enforce_tenant_row_level_security('product_variants');

ALTER TABLE product_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_categories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON product_categories FOR ALL
    USING (EXISTS (
        SELECT 1 FROM products p JOIN categories c ON c.id = category_id
        WHERE p.id = product_id AND p.tenant_id = c.tenant_id
            AND p.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM products p JOIN categories c ON c.id = category_id
        WHERE p.id = product_id AND p.tenant_id = c.tenant_id
            AND p.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ));

ALTER TABLE variant_attributes ENABLE ROW LEVEL SECURITY;
ALTER TABLE variant_attributes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON variant_attributes FOR ALL
    USING (EXISTS (
        SELECT 1 FROM product_variants v
        WHERE v.id = variant_id
            AND v.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM product_variants v
        WHERE v.id = variant_id
            AND v.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ));
