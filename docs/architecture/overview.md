# Architecture Overview

## Table of Contents

- [Modular Monolith Design](#modular-monolith-design)
- [Multi-Tenancy & Scaling Modes](#multi-tenancy--scaling-modes)
- [Data Isolation (PostgreSQL RLS)](#data-isolation-postgresql-rls)
- [Request Context & Correlation IDs](#request-context--correlation-ids)
- [Distributed Locking & Caching](#distributed-locking--caching)

## Modular Monolith Design

Rafaga is built with **Spring Boot 4.1.2** and **Spring Modulith 2.1.1**. Each business capability lives under `modules/<name>` and follows a three-layer structure:

```text
modules/<module>/src/main/java/com/rafaga/backend/<module>/
├── domain/          # Entities, value objects, domain rules, domain events
├── application/     # Use cases, commands, ports, exceptions (@NamedInterface("api"))
└── infrastructure/  # JPA adapters, REST controllers, HTTP clients, schedulers
```

`ModuleArchitectureTest` enforces strict boundaries during CI:
- Cross-module calls must go through `@NamedInterface("api")` interfaces/records or `@NamedInterface("events")` Spring Modulith events.
- Direct access to another module's JPA entities, repositories, or infrastructure classes fails the build.

## Multi-Tenancy & Scaling Modes

A tenant progresses through three deployment modes without code forks:

| Mode | Application Pool | Database | Routing |
| --- | --- | --- | --- |
| `SHARED_DB` | Shared backend | Shared PostgreSQL (isolated via forced RLS) | Traefik Gateway → `shared-pool` |
| `DEDICATED_DB` | Shared backend | Dedicated PostgreSQL per tenant (`TenantAwareRoutingDataSource`) | Traefik Gateway → `shared-pool` |
| `DEDICATED_INSTANCE` | Dedicated backend container | Dedicated PostgreSQL | Traefik Gateway → registered dedicated `target_id` |

Tenant identity is resolved per request from the verified `Host` / trusted `X-Forwarded-Host` header via `TenantContextFilter` and cached in Redis (`tenants` cache region).

## Data Isolation (PostgreSQL RLS)

- **Control-plane tables** (`tenants`, `tenant_domains`, `tenant_feature_flags`, `deployment_targets`, `tenant_target_transitions`) are read globally for host resolution and routing.
- **Business tables** (`products`, `categories`, `stock_levels`, `carts`, `orders`, `media_assets`, and association tables) enforce **PostgreSQL forced Row-Level Security** keyed on `app.current_tenant`.
- Runtime traffic always uses a restricted PostgreSQL role (`LOGIN NOSUPERUSER NOBYPASSRLS`) that never owns business tables; schema migrations run under a separate owner role.

## Request Context & Correlation IDs

- `RequestCorrelationFilter` validates an inbound UUID `X-Correlation-ID` (or generates a new one), binds it to SLF4J `MDC` (`correlationId`), and echoes it on the HTTP response.
- `CorrelationIdPropagation` forwards the validated `X-Correlation-ID` to internal outbound dependencies (Keycloak, target health probes, private gateway ops).
- Structured JSON logs include `correlationId` and `tenantId`, and strictly exclude credentials, tokens, and raw `Authorization` / `Cookie` headers.

## Distributed Locking & Caching

- **Redis** stores shared caches (`tenants`, `feature-flags`, `products`, `categories`) and distributed rate-limit counters (with a stricter bounded local fallback if Redis is temporarily unavailable).
- **ShedLock** (`@SchedulerLock` backed by PostgreSQL) ensures mutating scheduled jobs (inventory reservation expiry, media cleanup/reconciliation, domain activation worker) run on at most one instance at a time.
