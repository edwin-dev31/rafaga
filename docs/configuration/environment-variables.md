# Environment Variables Reference

All local defaults can be overridden via environment variables or `.env` (for development). Never commit real secrets to the repository.

## Table of Contents

- [Core & Server](#core--server)
- [PostgreSQL (Shared & Dedicated)](#postgresql-shared--dedicated)
- [Redis](#redis)
- [Keycloak & Authentication](#keycloak--authentication)
- [Gateway, CORS & Edge Security](#gateway-cors--edge-security)
- [Cloudflare SaaS & R2 Media](#cloudflare-saas--r2-media)
- [Rate Limits](#rate-limits)
- [Test Profile Overrides](#test-profile-overrides)

## Core & Server

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile (`dev`, `test`, `production`). |
| `RAFAGA_SERVER_PORT` | `3117` | HTTP server listen port. |
| `RAFAGA_SHUTDOWN_TIMEOUT` | `30s` | Maximum graceful-shutdown drain duration. |
| `RAFAGA_MAX_REQUEST_CONTENT_LENGTH` | `1MB` | Maximum declared JSON/API request body size. |

## PostgreSQL (Shared & Dedicated)

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `DB_SHARED_URL` | `jdbc:postgresql://localhost:5432/rafaga_shared` | Shared PostgreSQL JDBC URL. |
| `DB_SHARED_USER` | `rafaga_runtime` | Restricted runtime database role (`NOSUPERUSER NOBYPASSRLS`). |
| `DB_SHARED_PASSWORD` | `rafaga_runtime_dev` | Password for the restricted runtime role. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `rafaga_app` / `rafaga_dev` | Schema-owner role used only by Flyway and role-setup scripts. |
| `RAFAGA_TENANT_DATABASE_SECRETS_DIR` | `/run/secrets/tenant-db` | Directory containing `<secretReference>.json` files for `DEDICATED_DB` tenants. |
| `RAFAGA_TENANT_DB_SECRET_<REF>` | — | Alternative env-var JSON credentials for a `DEDICATED_DB` secret reference (normalized to uppercase `_`). |

## Redis

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Standalone Redis host and port (used in both dev and production). |
| `REDIS_PASSWORD` | — | Production Redis password. |
| `REDIS_SSL_ENABLED` | `false` (`dev`) / `true` (`prod`) | Enables TLS for Redis connections. |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_TIMEOUT` | `2s` / `2s` | Bounded Redis connection and command timeouts. |

## Keycloak & Authentication

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `KEYCLOAK_ISSUER_URI` | Local Keycloak realm URL | OIDC issuer URI used for JWT validation and token requests. |
| `KEYCLOAK_JWK_SET_URI` | Local Keycloak JWK endpoint | JWK Set URI for JWT signature verification. |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | — | Confidential client credentials for manager authentication and audience (`aud`) validation. |
| `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` | `rafaga-backend-admin` / — | Least-privilege service account for user provisioning. |
| `KEYCLOAK_CONNECT_TIMEOUT` / `KEYCLOAK_READ_TIMEOUT` | `2s` / `5s` | Bounded HTTP timeouts for Keycloak and JWKS requests. |
| `RAFAGA_IDENTITY_REGISTRATION_TOKEN` | — | Global secret required in `X-Registration-Token` for `POST /api/public/auth/register`. |
| `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` | `admin` / — | Local Docker Compose Keycloak bootstrap credentials (`dev` only). |

## Gateway, CORS & Edge Security

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `RAFAGA_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated exact origins allowed by CORS (dashboard, never wildcards). |
| `RAFAGA_TRUSTED_PROXY_CIDRS` | — | Comma-separated trusted Gateway/Cloudflare CIDRs for `X-Forwarded-*` headers in production. |
| `RAFAGA_TENANT_DIRECTORY_INTERNAL_TOKEN` | — | Shared secret authenticating `GET /api/internal/tenant-directory` from the Gateway sync worker. |
| `RAFAGA_GATEWAY_OPERATIONS_URL` | — | Optional private Gateway `/ops/status` URL queried on demand by `/api/admin/operations/overview`. |
| `RAFAGA_GATEWAY_OPERATIONS_TOKEN` | Falls back to directory token | Token sent in `X-Gateway-Operations-Token` when querying Gateway ops. |

## Cloudflare SaaS & R2 Media

| Variable | Default (`dev`) | Description |
| --- | --- | --- |
| `RAFAGA_CLOUDFLARE_ENABLED` | `false` | Enables Cloudflare for SaaS custom hostname provisioning and R2 CORS sync. |
| `RAFAGA_CLOUDFLARE_API_TOKEN` | — | Scoped Cloudflare API token. |
| `RAFAGA_CLOUDFLARE_ACCOUNT_ID` / `RAFAGA_CLOUDFLARE_ZONE_ID` | — | Cloudflare account and SaaS zone IDs. |
| `RAFAGA_MEDIA_R2_*` | See `.env.example` | R2 endpoint, bucket, public CDN host (`img.rafaga.app`), and bucket-scoped S3 credentials. |

## Rate Limits

| Variable | Default | Description |
| --- | --- | --- |
| `RAFAGA_RATE_LIMIT_ENABLED` | `true` | Enables backend tenant-aware rate limiting (`RateLimitFilter`). |
| `RAFAGA_RATE_LIMIT_WINDOW` | `1m` | Sliding window duration. |
| `RAFAGA_RATE_LIMIT_LOGIN_PER_WINDOW` | `10` | Max login attempts per window. |
| `RAFAGA_RATE_LIMIT_REGISTRATION_PER_WINDOW` | `3` | Max registration attempts per window. |
| `RAFAGA_RATE_LIMIT_MEDIA_UPLOAD_PER_WINDOW` | `12` | Max media upload initiations per window. |
| `RAFAGA_RATE_LIMIT_GUEST_CART_MUTATION_PER_WINDOW` | `30` | Max guest cart mutations per window. |
| `RAFAGA_RATE_LIMIT_CHECKOUT_PER_WINDOW` | `8` | Max checkout attempts per window. |

## Test Profile Overrides

| Variable | Default (`test`) | Description |
| --- | --- | --- |
| `TEST_DB_SHARED_URL` | `jdbc:postgresql://localhost:5433/rafaga_test` | Isolated test PostgreSQL URL. |
| `TEST_DB_SHARED_USER` / `TEST_DB_SHARED_PASSWORD` | `rafaga_test_runtime` | Restricted test runtime database role. |
| `TEST_POSTGRES_USER` / `TEST_POSTGRES_PASSWORD` | `rafaga_test` | Test schema-owner role for Flyway and role setup. |
| `TEST_REDIS_HOST` / `TEST_REDIS_PORT` | `localhost` / `6380` | Isolated test Redis instance. |
