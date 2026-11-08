ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_roles FOR ALL
    USING (EXISTS (
        SELECT 1 FROM users u JOIN roles r ON r.id = role_id
        WHERE u.id = user_id AND u.tenant_id = r.tenant_id
            AND u.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM users u JOIN roles r ON r.id = role_id
        WHERE u.id = user_id AND u.tenant_id = r.tenant_id
            AND u.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ));

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_permissions FOR ALL
    USING (EXISTS (
        SELECT 1 FROM roles r
        WHERE r.id = role_id
            AND r.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM roles r
        WHERE r.id = role_id
            AND r.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')
    ));
