# Rafaga Backend

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4.1.2](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.2-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis 8](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

Rafaga is a multi-tenant e-commerce SaaS backend built as a Java modular monolith. It supports an incremental tenant evolution from shared infrastructure to dedicated databases and dedicated application instances without forking business logic.

## Architecture

```mermaid
flowchart TB
    clients[Storefronts and Merchant Dashboard] -->|HTTPS| app[Rafaga Spring Boot Application]

    subgraph modules["Modular Monolith"]
        tenant[Tenant Module]
        identity[Identity Module]
        catalog[Catalog Module]
        inventory[Inventory Module]
        orders[Orders Module]
    end

    app --> tenant
    app --> identity
    app --> catalog
    app --> inventory
    app --> orders

    tenant --> registry[Tenant Registry]
    tenant --> cache[(Redis Cache)]
    tenant --> shared[(Shared PostgreSQL)]
    tenant --> dedicated[(Dedicated PostgreSQL)]
```

Each module follows `domain`, `application`, and `infrastructure` layers. The tenant application layer is organized by intent:

```text
application/
├── command/       # commands and input validation
├── exception/     # use-case errors
├── plan/          # plan feature defaults
├── port/          # infrastructure contracts
├── resolution/    # tenant resolution contract
└── service/       # use cases
```

## Modules

| Module | Responsibility |
| --- | --- |
| `common` | Cross-cutting contracts, including `TenantContext`. |
| `tenant` | Tenant registration, configuration, domains, feature flags, caching, and datasource routing. |
| `identity` | Identity, roles, and Keycloak/OIDC integration. |
| `catalog` | Products, categories, and variants. |
| `inventory` | Stock levels, movements, and reservations. |
| `orders` | Orders and their lifecycle. |

## Requirements

- JDK 25
- Docker Desktop or Docker Engine with Compose v2
- Git

Gradle does not need to be installed globally; the repository includes the Gradle Wrapper.

## Quick start

Run the following commands from the `backend` directory.

```powershell
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat bootRun
```

On macOS or Linux:

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

The default profile is `dev`. The health endpoint is available at `GET /actuator/health`.

To stop the development environment:

```powershell
docker compose down
```

Development PostgreSQL data is stored in the `rafaga_postgres_data` volume.

## Testing

Tests use isolated infrastructure: PostgreSQL on port `5433` and Redis on port `6380`. The test database is ephemeral.

```powershell
docker compose -f compose.test.yaml up -d
.\gradlew.bat test
docker compose -f compose.test.yaml down
```

The build automatically activates the Spring `test` profile. Datasource-routing integration tests use Testcontainers and also require Docker.

To run only the tenant module tests:

```powershell
.\gradlew.bat :modules:tenant:test
```

## Configuration

Local values can be overridden with environment variables.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_SHARED_URL` | `jdbc:postgresql://localhost:5432/rafaga_shared` | Development shared PostgreSQL URL. |
| `DB_SHARED_USER` | `rafaga_app` | Shared database user. |
| `DB_SHARED_PASSWORD` | `rafaga_dev` | Local development password. |
| `TEST_DB_SHARED_URL` | `jdbc:postgresql://localhost:5433/rafaga_test` | Test-profile PostgreSQL URL. |
| `TEST_DB_SHARED_USER` | `rafaga_test` | Test database user. |
| `TEST_DB_SHARED_PASSWORD` | `rafaga_test` | Test database password. |
| `TEST_REDIS_HOST` | `localhost` | Test Redis host. |
| `TEST_REDIS_PORT` | `6380` | Test Redis port. |

Dedicated-database credentials are never stored in `TenantConfig`. `TenantDatabaseCredentialsProvider` is the port used to integrate the appropriate secrets manager.

## Tenant management API

Administrative tenant endpoints are available under `/api/admin/tenants`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/admin/tenants` | Registers a tenant and initializes its plan feature flags. |
| `GET` | `/api/admin/tenants` | Lists registered tenants. |
| `GET` | `/api/admin/tenants/{tenantId}` | Retrieves a tenant configuration. |
| `PUT` | `/api/admin/tenants/{tenantId}` | Updates tenant configuration, domains, or plan. |

Example request:

```json
{
  "name": "Demo Store",
  "domains": ["demo.rafaga.test"],
  "deploymentMode": "SHARED_DB",
  "planId": "starter"
}
```

For `DEDICATED_DB`, provide `databaseConfigId` as a secret reference. Never send or persist raw connection credentials in a request body.

> Platform-role authorization and the concrete Keycloak integration are not available yet. Do not expose these endpoints publicly.

## Database migrations

Flyway runs migrations during application startup. Hibernate runs with `ddl-auto=validate`, so schema changes must be delivered through versioned SQL migrations in `src/main/resources/db/migration`.

- `V1__create_modulith_event_publications.sql`
- `V2__create_tenant_management.sql`

## Common commands

```powershell
# Run the full test suite
.\gradlew.bat test

# Start the application
.\gradlew.bat bootRun

# Build the executable JAR
.\gradlew.bat bootJar

# Clean local build artifacts
.\gradlew.bat clean
```

## Contribution guidelines

- Communicate across modules through public APIs or events; never access another module's internal entities or repositories.
- Associate and validate all business data against `TenantContext`.
- Add or update the Flyway migration in the same change that modifies a persistent model.
- Run `./gradlew test` before creating a functional commit.
- Use conventional commits: `feat(<module>):`, `test(<module>):`, `fix(<module>):`, `refactor(<module>):`, or `chore:`.
