-- Coordinates singleton scheduled work when the backend is scaled horizontally.
-- This operational table is global: it deliberately has no tenant_id or RLS policy.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
