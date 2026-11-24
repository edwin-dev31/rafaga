-- Link orders to source carts (idempotency per tenant), allow guest orders, and support unguessable public receipt tokens.
ALTER TABLE orders ADD COLUMN cart_id UUID;
ALTER TABLE orders ADD COLUMN public_token VARCHAR(64);
ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL;

CREATE UNIQUE INDEX uk_orders_tenant_cart
    ON orders (tenant_id, cart_id)
    WHERE cart_id IS NOT NULL;

CREATE UNIQUE INDEX uk_orders_tenant_public_token
    ON orders (tenant_id, public_token)
    WHERE public_token IS NOT NULL;

-- Store the created order reference and public receipt token on checked-out carts.
ALTER TABLE carts ADD COLUMN order_id UUID;
ALTER TABLE carts ADD COLUMN order_public_token VARCHAR(64);
