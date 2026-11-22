# Future Migration Plan: OAuth 2.0 Authorization Code + PKCE

## 1. Current Launch Scope (Unchanged)

For the initial production launch, store manager authentication intentionally uses the backend proxy endpoint `POST /api/public/auth/login` (Keycloak Direct Access Grants / ROPC) so the dashboard and backend release scope remains minimal and verifiable.

Launch hardening controls already in place:
- **Layered rate limiting**: Traefik edge limits (`rate-limit-login`, `rate-limit-register`, `max-body-auth`) plus backend Redis/in-memory tenant-aware limits (`RateLimitFilter`).
- **Bounded Keycloak HTTP timeouts**: `connectTimeout = 2s`, `readTimeout = 5s` on both `keycloakRestClient` and `NimbusJwtDecoder` JWKS fetches.
- **Strict JWT validation**: signature, expiration, issuer (`iss`), audience (`aud`), realm roles, and `tenant_id` claim verification.
- **Resilience on Keycloak outage**: existing unexpired JWTs continue to authorize requests using cached JWKS keys; expired, tampered, wrong-issuer, wrong-audience, or unknown-key tokens fail safely; login/registration return `503 Service Unavailable`.
- **Non-sensitive audit logs**: login, registration, and provisioning outcomes log only `event`, `outcome`, `tenantId`, and safe `reason` codes — never passwords, tokens, emails, or `Authorization` headers.

## 2. Target Architecture: Authorization Code + PKCE (S256)

When the dashboard moves beyond initial launch, manager login will migrate from backend-proxied ROPC to standard **OAuth 2.0 Authorization Code Flow with PKCE (`S256`)**:

1. **Keycloak client configuration**:
   - Create a dedicated public SPA client (e.g., `rafaga-dashboard-web`) or BFF confidential client in the `rafaga` realm.
   - Enable **Standard Flow** (`authorization_code`) and require **PKCE Code Challenge Method = `S256`**.
   - Disable **Direct Access Grants** on the new client.
   - Register exact redirect URIs and post-logout redirect URIs (e.g., `https://app.rafaga.app/auth/callback`) — never wildcards.
   - Preserve the existing audience mapper (`aud` includes the backend API client ID) and `tenant_id` / `realm_access.roles` mappers so backend resource-server validation requires zero breaking changes.

2. **Dashboard authentication flow**:
   - Dashboard generates a high-entropy `code_verifier` and `code_challenge = BASE64URL(SHA256(code_verifier))`.
   - Browser redirects directly to Keycloak (`https://auth.rafaga.app/realms/rafaga/protocol/openid-connect/auth`) — passwords never pass through the Rafaga backend.
   - Keycloak redirects back with `?code=...&state=...`; the dashboard exchanges `code` + `code_verifier` at `/protocol/openid-connect/token` (or via a thin BFF cookie session if HttpOnly cookies are adopted).
   - Enable short-lived access tokens (5–15 min) and rotating single-use refresh tokens in Keycloak.

3. **Backend resource server compatibility**:
   - `SecurityConfig.jwtDecoder` already validates `iss`, `aud`, signature, and expiration independently of how the token was obtained.
   - `UserService.getCurrentUser()` already synchronizes manager profiles from JWT claims on first access without calling Keycloak `/userinfo`.

## 3. Phased Rollout & Rollback

| Phase | Action | Rollback |
| --- | --- | --- |
| **Phase 1 (Dual support)** | Provision PKCE client in Keycloak; update dashboard behind a feature flag; keep `POST /api/public/auth/login` active. | Flip dashboard feature flag back to backend login. |
| **Phase 2 (Observe & cut over)** | Enable PKCE for internal and pilot tenants; verify `aud`, `tenant_id`, role claims, and refresh rotation; roll out to 100% of managers. | Re-enable legacy login flag; ROPC endpoint still live. |
| **Phase 3 (Decommission ROPC)** | Remove `POST /api/public/auth/login` after 30 days of zero traffic and disable Direct Access Grants on the backend client in Keycloak. | Re-enable Direct Access Grants in Keycloak and restore previous backend image if emergency fallback is required. |
