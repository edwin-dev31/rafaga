# Deployment guide

## Prerequisites

- Java 25 and a PostgreSQL version supported by the JDBC driver.
- Redis or Redis Cluster reachable over the configured TLS policy.
- A Keycloak realm with the `SUPER_ADMIN`, `STORE_MANAGER`, and `CUSTOMER`
  roles, plus clients for backend authentication and backend administration.
- A secrets manager or equivalent implementation of
  `TenantDatabaseCredentialsProvider` for dedicated databases.

Set `SPRING_PROFILES_ACTIVE=production`. Provide database, Redis, Keycloak,
CORS, and registration-token values through the environment; their names and
required production settings are listed in [Environment Variables](../configuration/environment-variables.md).
Never place credentials in source control, tenant records, command lines, or
application logs.

## Shared database deployment

1. Create a schema-owner account for Flyway and a restricted runtime account
   for the application. The runtime account must not bypass row-level security.
2. Set `DB_SHARED_URL`, `DB_SHARED_USER`, and `DB_SHARED_PASSWORD` to the
   runtime connection. Flyway uses the configured migration connection.
3. Run the role-setup process after PostgreSQL starts so the runtime account has
   the required grants.
4. Start Rafaga with the `production` profile. Startup applies migrations before
   serving traffic and fails closed if a migration fails.
5. Verify `GET /actuator/health/liveness` and `/actuator/health/readiness`.
   In production, health output contains no component details.

## Dedicated database tenants

1. Provision a dedicated database and its restricted runtime role.
2. Store both runtime and schema-owner credentials in the external secrets
   manager; do not persist them in `TenantConfig`.
3. Implement and configure `TenantDatabaseCredentialsProvider` to resolve those
   credentials by tenant ID.
4. Register or update the tenant with deployment mode `DEDICATED_DB` through the
   super-admin tenant API.
5. Rafaga migrates the dedicated database before its pool becomes usable. If
   migration or credential resolution fails, requests for that tenant fail
   instead of falling back to the shared database.
6. Check the super-admin operational health endpoint after provisioning to
   confirm the dedicated pool is available.

## Migration operations

Flyway migration files live under `src/main/resources/db/migration`. Add new
versioned, forward-only SQL migrations; do not edit an applied migration or
reuse a version. Development-only fixtures live under `db/dev-migration` and
are excluded from production and test profiles. The V12 migration deliberately
removes legacy local role tables because Keycloak is the authorization source of
truth.

Back up production databases before a schema change. Test a migration on a
production-like copy, deploy it through the normal application startup flow,
then verify health and tenant isolation. Roll forward with a new corrective
migration rather than changing migration history.

## Monitoring and operations

- Scrape the public Kubernetes health probes only; keep detailed operations
  behind `SUPER_ADMIN` access.
- Alert on `DOWN` shared or dedicated databases and Keycloak. Redis may report
  `DEGRADED`; the application continues by bypassing cache operations.
- Use structured logs with `tenantId` for investigation. Never export request
  tokens, passwords, registration tokens, or payment data to logging systems.
- Use the super-admin cache invalidation endpoint only for operational recovery;
  normal domain updates evict their own cache entries.
