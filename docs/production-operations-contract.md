# Production operations contract

This is the launch contract for the initial one-Gateway/one-backend topology.
It is deliberately explicit about what belongs to the backend repository and
what the platform operator must set privately before a deployment.

## Initial inventory

| Role | Initial placement | Network rule | Owner |
| --- | --- | --- | --- |
| Traefik Gateway and backend | One application VPS, separate Docker Swarm services | Only Traefik publishes `80`/`443`; backend has no published host port | Platform operations owner |
| PostgreSQL | Independent data VPS | Accepts `5432` only from the application private network and backup worker | Platform operations owner |
| Redis | Independent data service/VPS, recorded in `PLATFORM_REDIS_LOCATION` | Accepts its TLS port only from application private network | Platform operations owner |
| Keycloak and its PostgreSQL | Independent identity VPS | Public OIDC hostname through Cloudflare; administration and database stay private | Identity operator |
| Cloudflare and R2 | Managed services | Cloudflare is the only public DNS/TLS/WAF edge; R2 is reached with scoped credentials | Platform operations owner |

The private `/opt/rafaga/backend.env` records actual VPS names/IPs, the
Traefik public hostname, Keycloak public hostname, Redis placement, the named
operations owner, and alert recipients. The deployment script rejects empty or
template values, so these facts cannot be silently omitted.

## Firewall and DNS ownership

- Cloudflare DNS points each public hostname only to Traefik. Direct backend,
  PostgreSQL, Redis, Keycloak administration, and database hostnames have no
  public DNS record.
- The application VPS allows inbound `80` and `443` only to Traefik. Docker
  overlay and private dependency traffic are restricted to the private network.
- The data VPS allows PostgreSQL/Redis only from the application private CIDR.
  The identity VPS allows Keycloak administration only from the operations
  network.
- `RAFAGA_TRUSTED_PROXY_CIDRS` contains only the private Traefik CIDRs, never
  a public or all-address range.

## Recovery objectives and acceptance

The initial accepted operating targets are **RPO 15 minutes** and **RTO 60
minutes**. Keep encrypted off-VPS backups for 35 daily recovery points, 12
weekly recovery points, and 12 monthly recovery points. PostgreSQL WAL/archive
or equivalent must meet the 15-minute RPO; Keycloak database backups follow the
same schedule. Redis is cache-only unless a future feature explicitly changes
that contract.

Before first customer activation, the platform owner records their name and at
least one monitored alert destination in `PLATFORM_OPERATIONS_OWNER` and
`PLATFORM_ALERT_RECIPIENTS`. They accept these targets by performing a restore
rehearsal and recording the measured restoration time outside source control.

## Secret and image lifecycle

Credentials are Docker Swarm Secrets, named with a revision such as
`rafaga_media_r2_secret_access_key_20261120-01`. The backend image mounts only
the secrets it consumes; Cloudflare and Traefik credentials are provisioned in
the same inventory but are not mounted until their respective deployments
exist. See [production secrets and rotation](production-secrets-and-rotation.md).

Backend release images use a GHCR commit-SHA tag. The deployment script rejects
`latest` and snapshot tags. Traefik and other future service images must use a
version plus immutable digest in their own Swarm stack; they are intentionally
not deployed before the Traefik task.

## Later replica expansion

The first application VPS is intentionally a single point of failure. Adding
backend replicas on it improves container restart and rolling updates, not VPS
availability. The next expansion adds a second application VPS, joins it to the
private overlay, reduces per-instance Hikari capacity, and introduces Traefik
health-checked targets only after the routing and capacity tasks are complete.
