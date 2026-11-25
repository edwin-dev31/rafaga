# API guide

## Base URLs and interactive specification

The API is served under the application base URL. With the `dev` profile, the
interactive Scalar UI is available at `/scalar` and the generated OpenAPI JSON
is available at `/v3/api-docs`. Both are disabled by the `production` profile.

## Authentication and roles

Protected endpoints require `Authorization: Bearer <access-token>`. Rafaga
validates Keycloak JWTs and reads roles from the `roles` or
`realm_access.roles` claim. Keycloak is the source of truth for these roles:

| Role | Access |
| --- | --- |
| `SUPER_ADMIN` | Platform administration, tenant administration, operational endpoints, and all backoffice operations. |
| `STORE_MANAGER` | Tenant-scoped catalog, inventory, orders, carts, and own profile. |
| `CUSTOMER` | Storefront customer flows only; it cannot use backoffice endpoints. |

`POST /api/public/auth/login`, `POST /api/public/auth/refresh`, and `POST /api/public/auth/logout`
are public session endpoints backed by Keycloak (see [auth-pkce-migration.md](../security/auth-pkce-migration.md)
for the planned Authorization Code + PKCE transition). `POST /api/public/auth/register`
requires both a `SUPER_ADMIN` token and the `X-Registration-Token` header; for dashboard
onboarding, `SUPER_ADMIN` should prefer `POST /api/admin/tenants/{tenantId}/users`. All other
`/api/public/**` paths are denied by default. See [role-access-strategy.md](../security/role-access-strategy.md)
for the complete authorization matrix.

## Tenant resolution

For tenant-scoped requests the API resolves the tenant in this order:

1. The validated JWT `tenant_id` claim.
2. The internal `X-Tenant-ID` header when that trusted, CIDR-restricted feature
   is enabled.
3. The request host, matched against a tenant domain.

The JWT tenant must match the host-resolved tenant. `SUPER_ADMIN` is the sole
cross-tenant exception. A request with no resolvable tenant returns `400`; a
mismatch returns `401`; inactive tenants return `404`.

## Endpoint groups

| Group | Paths | Access |
| --- | --- | --- |
| Health probes | `GET /actuator/health/**` | Public, minimal response in production. |
| Authentication | `/api/public/auth/login`, `/api/public/auth/refresh`, `/api/public/auth/logout`, `/api/public/auth/register` | Public session endpoints with dedicated rate limits; `register` requires `SUPER_ADMIN` + `X-Registration-Token`. |
| Storefront | `/api/storefront/**` | Public catalog and guest-cart operations; customer endpoints validate the customer JWT and ownership. |
| Backoffice catalog | `/api/products/**`, `/api/categories/**` | `SUPER_ADMIN` or `STORE_MANAGER`; requires the `catalog` feature. |
| Backoffice inventory | `/api/stock/**` | `SUPER_ADMIN` or `STORE_MANAGER`; requires the `inventory` feature. |
| Backoffice orders | `/api/orders/**` | `SUPER_ADMIN` or `STORE_MANAGER`; requires the `orders` feature. |
| Backoffice carts | `/api/carts/**` | `SUPER_ADMIN` or `STORE_MANAGER`; ownership checks remain in force. |
| Users | `/api/users/me`, `/api/users/**` | `me`: `SUPER_ADMIN` or `STORE_MANAGER`; all other user operations: `SUPER_ADMIN`. |
| Administration | `/api/admin/**`, non-health actuator endpoints | `SUPER_ADMIN` only. |

`GET /api/admin/health` returns a status-only aggregation of shared database,
dedicated database, Redis, and Keycloak checks. `POST
/api/admin/cache/invalidate?cache={tenants|feature-flags|products|categories}`
clears one named cache region. Neither endpoint returns infrastructure secrets.

## Common responses

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid payload or tenant could not be resolved. |
| `401 Unauthorized` | No valid JWT or its `tenant_id` conflicts with the request tenant. |
| `403 Forbidden` | Role, ownership, or required tenant feature does not permit the operation. |
| `404 Not Found` | The resource is absent from the requesting tenant/customer scope, or tenant is inactive. |
| `409 Conflict` | A duplicate business value, invalid order transition, or insufficient stock conflict. |
| `503 Service Unavailable` | A critical health dependency is unavailable. |

Controllers expose DTOs only. Do not infer persistence IDs, secrets, internal
tenant configuration, or Keycloak administration data from API responses.
