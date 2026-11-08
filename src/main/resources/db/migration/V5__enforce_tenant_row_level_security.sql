CREATE OR REPLACE FUNCTION enforce_tenant_row_level_security(target_table REGCLASS)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target_table);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', target_table);
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %s', target_table);
    EXECUTE format(
        'CREATE POLICY tenant_isolation ON %s FOR ALL '
        || 'USING (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')) '
        || 'WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), ''''))',
        target_table);
END;
$$;

SELECT enforce_tenant_row_level_security('users');
SELECT enforce_tenant_row_level_security('roles');
