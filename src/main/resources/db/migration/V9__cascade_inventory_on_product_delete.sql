ALTER TABLE stock_reservations DROP CONSTRAINT fk_stock_reservations_variant;
ALTER TABLE stock_reservations ADD CONSTRAINT fk_stock_reservations_variant
    FOREIGN KEY (tenant_id, product_id, variant_id)
    REFERENCES product_variants (tenant_id, product_id, id) ON DELETE CASCADE;
ALTER TABLE stock_reservations DROP CONSTRAINT fk_stock_reservations_product;
ALTER TABLE stock_reservations ADD CONSTRAINT fk_stock_reservations_product
    FOREIGN KEY (tenant_id, product_id)
    REFERENCES products (tenant_id, id) ON DELETE CASCADE;

ALTER TABLE inventory_movements DROP CONSTRAINT fk_inventory_movements_variant;
ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_movements_variant
    FOREIGN KEY (tenant_id, product_id, variant_id)
    REFERENCES product_variants (tenant_id, product_id, id) ON DELETE CASCADE;
ALTER TABLE inventory_movements DROP CONSTRAINT fk_inventory_movements_product;
ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_movements_product
    FOREIGN KEY (tenant_id, product_id)
    REFERENCES products (tenant_id, id) ON DELETE CASCADE;

ALTER TABLE stock_levels DROP CONSTRAINT fk_stock_levels_variant;
ALTER TABLE stock_levels ADD CONSTRAINT fk_stock_levels_variant
    FOREIGN KEY (tenant_id, product_id, variant_id)
    REFERENCES product_variants (tenant_id, product_id, id) ON DELETE CASCADE;
ALTER TABLE stock_levels DROP CONSTRAINT fk_stock_levels_product;
ALTER TABLE stock_levels ADD CONSTRAINT fk_stock_levels_product
    FOREIGN KEY (tenant_id, product_id)
    REFERENCES products (tenant_id, id) ON DELETE CASCADE;
