# Dedicated Tenant Database Provisioning, Migration & Restore Runbook

This runbook defines the repeatable workflow for provisioning, migrating, verifying RLS, backing up, and rolling back a `DEDICATED_DB` or `DEDICATED_INSTANCE` tenant database.

## 1. Provision Dedicated PostgreSQL Database & Least-Privilege Roles

Create two distinct PostgreSQL roles on the dedicated database:
- **Schema owner (migration role)**: used only by `TenantDatabaseMigrator` during Flyway migrations.
- **Restricted runtime role**: `LOGIN NOSUPERUSER NOBYPASSRLS`, used by `TenantAwareRoutingDataSource` for all runtime queries.

```bash
POSTGRES_HOST=<DEDICATED_PG_HOST> \
POSTGRES_DB=<TENANT_DB> \
POSTGRES_USER=<MIGRATION_OWNER_USER> \
POSTGRES_PASSWORD=<MIGRATION_OWNER_PASSWORD> \
DB_SHARED_USER=<TENANT_RUNTIME_USER> \
DB_SHARED_PASSWORD=<TENANT_RUNTIME_PASSWORD> \
./scripts/postgres/ensure-runtime-role.sh
```

## 2. Store Secret Reference

Mount the secret JSON under `${RAFAGA_TENANT_DATABASE_SECRETS_DIR:-/run/secrets/tenant-db}/<secretReference>.json` (or `RAFAGA_TENANT_DB_SECRET_<NORMALIZED_REF>`):

```json
{
  "jdbcUrl": "jdbc:postgresql://<HOST>:5432/<TENANT_DB>?sslmode=require",
  "runtimeUsername": "<TENANT_RUNTIME_USER>",
  "runtimePassword": "<TENANT_RUNTIME_PASSWORD>",
  "migrationUsername": "<MIGRATION_OWNER_USER>",
  "migrationPassword": "<MIGRATION_OWNER_PASSWORD>"
}
```

`FileOrEnvTenantDatabaseCredentialsProvider` enforces that `runtimeUsername` is never a superuser name (`postgres`, `root`, `admin`, `superuser`) and is strictly distinct from `migrationUsername`.

## 3. Forward-Only Migration & RLS Verification

`TenantDatabaseMigrator` executes all forward-only Flyway migrations (`classpath:db/migration`) before routing the first connection. Verify RLS enforcement:

```sql
SELECT relname, relrowsecurity, relforcerowsecurity
FROM pg_class
WHERE relname IN ('products', 'categories', 'orders', 'carts', 'stock_levels', 'media_assets');
```

All business tables MUST report `relrowsecurity = true` and `relforcerowsecurity = true`.

## 4. Encrypted Backup & Restore Rehearsal

```bash
# Encrypted off-VPS backup
pg_dump --format=custom --no-owner --host=<HOST> --username=<MIGRATION_OWNER_USER> <TENANT_DB> \
  | openssl enc -aes-256-cbc -pbkdf2 -salt -pass env:BACKUP_ENCRYPTION_KEY \
  > backup-<TENANT_DB>-$(date -u +%Y%m%dT%H%M%SZ).dump.enc

# Restore rehearsal into a disposable database
openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY -in <BACKUP_FILE> \
  | pg_restore --clean --if-exists --no-owner --host=<REHEARSAL_HOST> --username=<OWNER> --dbname=<REHEARSAL_DB>
```

## 5. Controlled Transition & Rollback

Use the super-admin transition API (`/api/admin/tenants/{tenantId}/target-transitions`):
1. `POST /prepare` (verifies destination target is `HEALTHY`).
2. `POST /{id}/validate` (records `dataValidationChecksum` and re-probes live target health).
3. `POST /{id}/activate` (atomically switches `tenant_domains.target_id` and advances `directory_version`).
4. `POST /{id}/drain` (completes transition after draining in-flight traffic).
5. **Rollback**: `POST /{id}/rollback` immediately restores `from_target_id`, advances `directory_version`, and invalidates cache with no backend or gateway restart.
