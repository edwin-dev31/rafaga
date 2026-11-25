# Role and access strategy

## Purpose

Rafaga separates platform administration, daily store operations, and storefront purchasing. Authorization is determined exclusively from Keycloak roles in the JWT; the application does not maintain a local authorization model.

## Roles

| Role | Purpose | Scope |
| --- | --- | --- |
| `SUPER_ADMIN` | Platform operations and support. | Every tenant. |
| `STORE_MANAGER` | Day-to-day store administration. | Only the tenant in the JWT. |
| `CUSTOMER` | Reserved for a future customer sign-in flow. | No backoffice access. |

Keycloak is the single source of truth for these roles. The development realm is defined in `config/keycloak/import/rafaga-realm.json`; JWT roles are mapped to Spring authorities with the `ROLE_` prefix.

## Access matrix

| Resource | `SUPER_ADMIN` | `STORE_MANAGER` | `CUSTOMER` / guest |
| --- | --- | --- | --- |
| `/actuator/health/**` | Yes | Yes | Yes |
| Remaining `/actuator/**` endpoints | Yes | No | No |
| `/api/public/auth/login` | Yes | Yes | Yes |
| `/api/public/auth/register` | Yes, with global token | No | No |
| `/api/admin/**` | Yes | No | No |
| `/api/users/me` | Yes | Yes | No |
| Administrative `/api/users/**` | Yes | No | No |
| Products, categories, stock, and orders | Yes | Yes | No |
| `/api/carts/**` | Yes | Yes, assisted carts within their own tenant | No |
| `/api/storefront/**` | Yes | Yes | Yes |

Unlisted routes under `/api/public/**` are denied by default. A new public endpoint requires an explicit rule in `SecurityConfig` and an authorization test.

## Tenant isolation

`TenantContextFilter` resolves the tenant from the request domain and compares it with the JWT `tenant_id` claim. A `STORE_MANAGER` can operate only in the tenant bound to its token. `SUPER_ADMIN` is the sole exception: it may use another tenant's domain for support or to onboard that store's manager.

This check is reinforced by tenant-scoped queries and PostgreSQL row-level security. A new backoffice route must never trust a tenant identifier supplied by the client alone.

## Key workflows

### Onboarding a store

1. `SUPER_ADMIN` creates the tenant through `/api/admin/tenants`.
2. Using the new tenant domain as the `Host`, it calls `POST /api/public/auth/register`.
3. The request must include a `SUPER_ADMIN` JWT and `X-Registration-Token`.
4. The backend creates the user in Keycloak with the resolved `tenant_id` and assigns `STORE_MANAGER`.

The global registration token is a second barrier; it never replaces JWT authorization.

### Authenticated carts

`/api/carts/**` is an authenticated backoffice API. A `STORE_MANAGER` may consult, modify, and check out assisted carts only for customers (`customers` or `users`) belonging to the same tenant bound to its JWT. A `SUPER_ADMIN` may access any cart for support. Guests use only `/api/storefront/**` and a guest session; they never provide a client-controlled `customerId`.

## Rules for future changes

- Apply least privilege: deny a new route until an explicit role rule exists.
- Do not reintroduce local `roles`, `role_permissions`, or `user_roles` tables. Migration `V12` removed them so Keycloak JWTs remain the only authorization source.
- Never allow `STORE_MANAGER` to cross tenants or access carts belonging to another tenant's customers.
- Keep the storefront public. A future customer sign-in flow may add `CUSTOMER` rules without making backoffice routes public.
- Add authorization tests for `SUPER_ADMIN`, `STORE_MANAGER`, and an identity without a backoffice role whenever a protected route is added.

## Future role management

Role changes must be managed through Keycloak. If an administrative API is added for that purpose, it must modify Keycloak, require `SUPER_ADMIN`, audit the change, and never create a second local permission source.
