-- V14__create_media_module.sql
-- Media module tables: media_assets, product_images, tenant_media_quotas

CREATE TABLE media_assets (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants (id),
    upload_key      VARCHAR(1024) NOT NULL UNIQUE,
    upload_expires_at TIMESTAMPTZ NOT NULL,
    reserved_bytes  BIGINT NOT NULL,
    object_key      VARCHAR(1024) UNIQUE,
    declared_content_type VARCHAR(100) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    width           INTEGER,
    height          INTEGER,
    status          VARCHAR(16) NOT NULL,
    asset_type      VARCHAR(16) NOT NULL,
    failure_code    VARCHAR(100),
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    delete_requested_at TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uk_media_assets_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_media_assets_status CHECK (status IN ('PENDING', 'READY', 'PENDING_DELETE', 'DELETED', 'FAILED')),
    CONSTRAINT ck_media_assets_type CHECK (asset_type IN ('PRODUCT', 'LOGO', 'BANNER')),
    CONSTRAINT ck_media_assets_reserved_bytes CHECK (reserved_bytes >= 0),
    CONSTRAINT ck_media_assets_dimensions CHECK ((width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)),
    CONSTRAINT ck_media_assets_size CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE INDEX idx_media_assets_tenant_status ON media_assets(tenant_id, status);
CREATE INDEX idx_media_assets_expired_pending ON media_assets(upload_expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_media_assets_pending_delete ON media_assets(delete_requested_at) WHERE status = 'PENDING_DELETE';
CREATE UNIQUE INDEX uk_media_assets_active_branding
    ON media_assets(tenant_id, asset_type)
    WHERE status = 'READY' AND asset_type IN ('LOGO', 'BANNER');

CREATE TABLE product_images (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    product_id      UUID NOT NULL,
    media_asset_id  UUID NOT NULL,
    position        INTEGER NOT NULL DEFAULT 0,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_product_images_asset UNIQUE (tenant_id, product_id, media_asset_id),
    CONSTRAINT uk_product_images_position UNIQUE (tenant_id, product_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_product_images_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_product_images_asset FOREIGN KEY (tenant_id, media_asset_id)
        REFERENCES media_assets (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_images_position CHECK (position >= 0)
);

CREATE INDEX idx_product_images_product ON product_images(tenant_id, product_id);
CREATE UNIQUE INDEX uk_product_images_primary
    ON product_images(tenant_id, product_id) WHERE is_primary;

CREATE TABLE tenant_media_quotas (
    tenant_id           VARCHAR(36) PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    max_assets          INTEGER NOT NULL,
    max_storage_bytes   BIGINT NOT NULL,
    used_assets         INTEGER NOT NULL DEFAULT 0,
    used_storage_bytes  BIGINT NOT NULL DEFAULT 0,
    reserved_assets     INTEGER NOT NULL DEFAULT 0,
    reserved_bytes      BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_tenant_media_quota_limits CHECK (max_assets >= 0 AND max_storage_bytes >= 0),
    CONSTRAINT ck_tenant_media_quota_usage CHECK (
        used_assets >= 0 AND used_storage_bytes >= 0
        AND reserved_assets >= 0 AND reserved_bytes >= 0)
);

-- Apply Row Level Security for tenant isolation
SELECT enforce_tenant_row_level_security('media_assets');
SELECT enforce_tenant_row_level_security('product_images');
SELECT enforce_tenant_row_level_security('tenant_media_quotas');
