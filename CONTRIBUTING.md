# Contributing to Rafaga Backend

Thank you for contributing to the Rafaga backend. Please follow these engineering standards so the modular monolith remains isolated, verifiable, and safe to deploy.

## Table of Contents

- [Architectural Boundaries](#architectural-boundaries)
- [Multi-Tenant Isolation](#multi-tenant-isolation)
- [Database Migrations](#database-migrations)
- [Logging & Security Hygiene](#logging--security-hygiene)
- [Commit Conventions](#commit-conventions)
- [Pull Request Checklist](#pull-request-checklist)

## Architectural Boundaries

- Respect **Spring Modulith** module boundaries (`ModuleArchitectureTest` enforces them in CI).
- Communicate across modules **only** through `@NamedInterface("api")` contracts or `@NamedInterface("events")` domain events.
- Never import another module's internal entities, repositories, or infrastructure adapters.
- Declare explicit parameter names (`@PathVariable("...")`, `@RequestParam(name = "...")`, `@RequestHeader("...")`) on all Spring MVC controllers (`ControllerParameterNameTest`).
- Guard every mutating scheduled job with `@SchedulerLock` (`ScheduledJobLockConfigurationTest`).

## Multi-Tenant Isolation

- Associate and validate all business data against `TenantContext`.
- Never trust client-supplied `tenantId` fields on public or storefront endpoints; tenant resolution comes strictly from the verified request host.
- Any new business module that persists tenant data **must** add PostgreSQL forced Row-Level Security (RLS) policies and a mandatory cross-tenant integration test included in `.github/workflows/tenant-isolation.yml`.

## Database Migrations

- Hibernate runs with `ddl-auto=validate`; every schema change must ship with a forward-only Flyway SQL migration.
- Place new migrations under `src/main/resources/db/migration/` using the next sequential version number.
- Keep migrations compatible with both `SHARED_DB` and `DEDICATED_DB` deployments, and update `TenantDatabaseMigratorIntegrationTest` if the expected migration count changes.
- See [Database Migrations](docs/architecture/database-migrations.md) for details.

## Logging & Security Hygiene

- Logs are structured JSON and include `correlationId` and `tenantId` where available.
- **Never** log passwords, OAuth/JWT tokens, registration tokens, secret references, credentials, or complete `Authorization` / `Cookie` headers.
- Health, admin, and operations endpoints must never expose connection strings, credentials, raw exception messages, or unbounded identifiers.

## Commit Conventions

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat(<module>): add controlled target transition rollback
fix(<module>): guard null cache entries in tenant config cache
test(<module>): verify JWT validation during Keycloak outage
docs(<area>): reorganize documentation structure and index
refactor(<module>): extract read-only tenant operations query
chore: update CI workflow matrix
```

## Pull Request Checklist

- [ ] Code compiles cleanly on Java 25 (`./gradlew test` passes locally).
- [ ] New or modified behavior includes unit and/or integration tests.
- [ ] All Spring Modulith, controller parameter, and scheduled-lock tests pass.
- [ ] Documentation under `docs/` is updated if endpoints, configuration, or operational procedures changed.
- [ ] CI `verify` job passes before requesting merge to `main`.
