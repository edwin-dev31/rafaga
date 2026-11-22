# Developer onboarding

## Start locally

From `backend`, copy `.env.example` to `.env`, start the development Compose
stack, run the PostgreSQL role setup job, and start the application:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose run --rm postgres-role-setup
.\gradlew.bat bootRun
```

The development profile seeds `tenant-demo`, its domain and flags, and a
development-only `DEMO-TEE` product. The imported Keycloak realm includes
`platform-admin`; sign in through `/api/public/auth/login` as described in the
root README. Roles are defined in Keycloak, never in the application database.

## Module conventions

Each module is split into `domain`, `application`, and `infrastructure`:

- `domain` contains business models and domain events.
- `application` contains commands, use cases, public ports, and module APIs.
- `infrastructure` contains JPA adapters, web controllers, messaging, and
  framework configuration.

Cross-module calls must depend on public application APIs or published events,
not another module's repositories or domain entities. Run the Modulith
architecture tests after changing module boundaries.

## Tenant isolation checklist

Every persisted tenant-owned record needs `tenant_id`, appropriate database
indexes, explicit repository filtering, and PostgreSQL row-level security.
Every HTTP operation must derive its tenant from the resolver/filter rather than
from a client-provided identifier. Never accept a customer ID, tenant ID, or
owner ID as an authority claim in a request body.

For a new tenant-facing feature:

1. Add its feature flag default to `DefaultPlanFeatureDefaults`.
2. Annotate guarded controllers with `@RequiresFeature("feature-name")`.
3. Preserve `404` behavior for cross-tenant or foreign-customer resources.
4. Add multi-tenant integration coverage using the existing isolation fixtures.
5. Add the module to the mandatory isolation CI gate when it stores tenant data.

## Testing and coverage

Start `compose.test.yaml` and its role setup job, then run the full suite:

```powershell
.\gradlew.bat test
.\gradlew.bat jacocoTestReport
```

The coverage report is written to `build/reports/jacoco/test/html/index.html`.
The mandatory API isolation tests are `CatalogApiIntegrationTest`,
`InventoryApiIntegrationTest`, and `OrderApiIntegrationTest`; the end-to-end
flow and role/feature checks complement them. Testcontainers-based routing tests
also require Docker.

## Security rules

Use constructor injection, validation DTOs, and transactions at application
service boundaries. Do not log secrets. Keep production defaults restrictive:
only health probes are public, OpenAPI UI is disabled, and administration routes
require `SUPER_ADMIN`. Consult [role-access-strategy.md](../security/role-access-strategy.md)
before changing an endpoint's authorization.
