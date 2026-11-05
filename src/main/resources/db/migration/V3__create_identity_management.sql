CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    keycloak_id VARCHAR(255) NOT NULL,
    username VARCHAR(150) NOT NULL,
    email VARCHAR(320) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_tenant_keycloak UNIQUE (tenant_id, keycloak_id),
    CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username)
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenants (id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_roles_tenant_id ON roles (tenant_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
