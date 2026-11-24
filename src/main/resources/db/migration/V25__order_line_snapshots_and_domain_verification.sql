-- Persist historical product/variant snapshots on order lines and Cloudflare TXT verification records on domains.
ALTER TABLE order_lines ADD COLUMN product_name VARCHAR(255);
ALTER TABLE order_lines ADD COLUMN sku VARCHAR(120);
ALTER TABLE order_lines ADD COLUMN variant_name VARCHAR(255);

ALTER TABLE tenant_domains ADD COLUMN ownership_verification_name VARCHAR(255);
ALTER TABLE tenant_domains ADD COLUMN ownership_verification_value VARCHAR(512);
