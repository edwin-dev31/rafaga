# Testing Guide

## Table of Contents

- [Test Infrastructure](#test-infrastructure)
- [Running Tests](#running-tests)
- [Mandatory Tenant-Isolation Gate](#mandatory-tenant-isolation-gate)
- [Architecture & Safety Gates](#architecture--safety-gates)
- [CI Workflow](#ci-workflow)

## Test Infrastructure

Integration tests use isolated, ephemeral containers defined in `compose.test.yaml`:
- **PostgreSQL**: port `5433` (`rafaga_test`)
- **Redis**: port `6380`
- **Keycloak**: port `8081` (auto-imports `config/keycloak/import/rafaga-realm.json`)

Datasource-routing and Flyway migration tests (`TenantDatabaseMigratorIntegrationTest`) additionally spin up disposable PostgreSQL instances via **Testcontainers** (Docker required).

## Running Tests

Start the test stack, run the full suite, and tear it down:

```bash
docker compose -f compose.test.yaml up -d --wait postgres-test redis-test keycloak-test
docker compose -f compose.test.yaml run --rm postgres-role-setup-test
./gradlew test
docker compose -f compose.test.yaml down
```

On Windows PowerShell, use `.\gradlew.bat test`.

Run tests for a single module:

```bash
./gradlew :modules:tenant:test
./gradlew :modules:identity:test
```

Run a specific test class:

```bash
./gradlew :test --tests "*DedicatedTargetTransitionIntegrationTest"
```

## Mandatory Tenant-Isolation Gate

The following PostgreSQL-backed integration tests verify that tenant B can never read or mutate tenant A data through the API or JPA layer:

- `CatalogApiIntegrationTest`
- `InventoryApiIntegrationTest`
- `OrderApiIntegrationTest`
- `CartApiIntegrationTest`
- `StorefrontMultiTenantIsolationTest`

Run them directly:

```bash
./gradlew :test \
  --tests com.rafaga.backend.CatalogApiIntegrationTest \
  --tests com.rafaga.backend.InventoryApiIntegrationTest \
  --tests com.rafaga.backend.OrderApiIntegrationTest
```

Any new business module persisting tenant data must add an equivalent integration test and register it in `.github/workflows/tenant-isolation.yml`.

## Architecture & Safety Gates

- **`ModuleArchitectureTest` & `TenantControlPlaneArchitectureTest`**: enforce Spring Modulith boundaries and `@NamedInterface` exports.
- **`ControllerParameterNameTest`**: enforces explicit `@PathVariable`, `@RequestParam`, and `@RequestHeader` names on all controllers.
- **`ScheduledJobLockConfigurationTest`**: enforces `@SchedulerLock` on all mutating scheduled jobs.
- **`OperationsSecurityAndRedactionTest`**: verifies health/admin/operations endpoints never leak credentials, JDBC URLs, secret references, or exception details.
- **`KeycloakResilienceAndJwtHardeningTest`**: verifies cached-JWKS JWT validation during Keycloak outages and safe rejection of expired/tampered/revoked tokens.

## CI Workflow

`.github/workflows/backend-ci.yml` runs parallel jobs (`unit-tests`, `integration-tests`, `e2e-tests`) feeding into the required `verify` status check before building the container image (`image`).
