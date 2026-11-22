# Database Migrations

## Table of Contents

- [Flyway Strategy](#flyway-strategy)
- [Development Seeds vs. Production](#development-seeds-vs-production)
- [Shared & Dedicated Database Lifecycle](#shared--dedicated-database-lifecycle)
- [Row-Level Security & Role Separation](#row-level-security--role-separation)

## Flyway Strategy

- Hibernate runs with `ddl-auto=validate`; every schema change must be delivered as a forward-only versioned SQL migration.
- Historical scripts (`V1`–`V9`) remain in `src/main/resources/db/migration` without renaming or editing.
- New migrations follow one global version sequence (`V10+`, currently through `V21`) under `src/main/resources/db/migration/` (or `db/migration/{module}/`, which Flyway scans automatically).
- All migration SQL must be compatible with both `SHARED_DB` and `DEDICATED_DB` PostgreSQL instances.

## Development Seeds vs. Production

- Demo tenant seed data (`V4`) lives separately in `src/main/resources/db/dev-migration` and is loaded only when the `dev` profile is active.
- Fresh `test` and `production` databases never execute dev seeds.
- If an existing database already records `V4` in `flyway_schema_history`, startup includes it for checksum validation without re-executing it.

## Shared & Dedicated Database Lifecycle

1. On startup, `TenantDatabaseMigrator` migrates the **shared database** first, followed by every registered `DEDICATED_DB` tenant.
2. When a newly registered dedicated database receives its first connection, pending migrations are executed lazily before opening the runtime Hikari pool.
3. A failed startup migration halts boot; a failed lazy migration blocks that tenant's pool from opening without affecting other tenants.
4. Dedicated database migrations require `TenantDatabaseCredentialsProvider` (implemented by `FileOrEnvTenantDatabaseCredentialsProvider`) to provide distinct `runtimeUsername` and `migrationUsername` credentials.

## Row-Level Security & Role Separation

- **Schema owner (`POSTGRES_USER` / `migrationUsername`)**: owns tables and runs Flyway migrations only.
- **Restricted runtime role (`DB_SHARED_USER` / `runtimeUsername`)**: `LOGIN NOSUPERUSER NOBYPASSRLS`, granted least-privilege DML access by `scripts/postgres/ensure-runtime-role.sh` (and `flyway_schema_history` kept inaccessible).
- Every business table enforces `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY` based on `current_setting('app.current_tenant', true)`.
