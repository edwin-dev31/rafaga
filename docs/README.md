# Rafaga Backend Documentation

Welcome to the Rafaga backend documentation index. Use the sections below to navigate architecture, API contracts, configuration, security, and operational runbooks.

## Table of Contents

- [Getting Started](#getting-started)
- [Architecture](#architecture)
- [API](#api)
- [Configuration](#configuration)
- [Security](#security)
- [Operations](#operations)

---

## Getting Started

| Document | Description |
| --- | --- |
| [Developer Onboarding](getting-started/developer-onboarding.md) | Local environment setup, seeded demo tenant, Keycloak login, and daily workflow. |
| [Testing Guide](getting-started/testing.md) | Unit, integration, E2E, Testcontainers, mandatory tenant-isolation checks, and CI pipeline. |

## Architecture

| Document | Description |
| --- | --- |
| [Architecture Overview](architecture/overview.md) | Spring Modulith boundaries, multi-tenant isolation (`SHARED_DB`, `DEDICATED_DB`, `DEDICATED_INSTANCE`), RLS, caching, and correlation IDs. |
| [Database Migrations](architecture/database-migrations.md) | Flyway migration sequence, `dev-migration` seeds, shared vs. dedicated DB migration lifecycle, and RLS role grants. |

## API

| Document | Description |
| --- | --- |
| [API Guide](api/api-guide.md) | Base URLs, OpenAPI/Scalar endpoints, storefront vs. backoffice contracts, and super-admin control-plane APIs. |

## Configuration

| Document | Description |
| --- | --- |
| [Environment Variables](configuration/environment-variables.md) | Complete reference of runtime, database, Redis, Keycloak, Cloudflare/R2, Gateway, and rate-limit configuration variables. |

## Security

| Document | Description |
| --- | --- |
| [Role & Access Strategy](security/role-access-strategy.md) | Keycloak role matrix (`SUPER_ADMIN`, `STORE_MANAGER`, `CUSTOMER`), tenant-crossing rules, and authorization boundaries. |
| [Production Secrets & Rotation](security/production-secrets-and-rotation.md) | Secret inventory, provisioning rules, and rotation procedures. |
| [Future PKCE Migration Plan](security/auth-pkce-migration.md) | Phased roadmap to migrate manager authentication from backend ROPC to OAuth 2.0 Authorization Code + PKCE. |

## Operations

| Document | Description |
| --- | --- |
| [Deployment Guide](operations/deployment-guide.md) | Production container build, profiles, probes, and deployment steps. |
| [Production Operations Contract](operations/production-operations-contract.md) | Boundary between repository-managed assets and operator-managed production state. |
| [Dedicated Database Runbook](operations/dedicated-database-runbook.md) | Provisioning, least-privilege roles, RLS checks, encrypted backups/restore, and controlled target transitions/rollback. |
| [Cloudflare R2 & CDN Setup](operations/cloudflare-r2-cdn-setup.md) | Bucket provisioning, S3 credentials, CORS synchronization, and immutable media CDN delivery. |
| [Docker Swarm Deployment (Legacy/Optional)](operations/docker-swarm-deployment.md) | Optional single-replica Swarm stack reference (current production uses standalone Docker Compose / Coolify). |
