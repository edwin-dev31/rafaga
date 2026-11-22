# Production secrets and rotation

## Provisioning

1. Copy `deploy/production/backend.env.template` to
   `/opt/rafaga/backend.env` and fill only non-secret configuration and the
   revision identifier. Set permissions to `0600`.
2. Copy `deploy/production/secret-source.env.template` to a temporary,
   root-readable file. Fill it from the approved secret store and set `0600`.
3. Run `scripts/production/provision-swarm-secrets.sh <source> <revision>` on
   the Swarm manager. It creates immutable named secrets and never prints their
   values.
4. Remove the temporary secret source securely after the Swarm secrets exist.
   Keep the authoritative values only in the approved secret store.
5. Deploy through `scripts/production/deploy-swarm.sh`; it verifies that every
   backend secret for the declared revision exists before changing the service.

## Rotation procedure

Use a new revision for every rotation; Docker Swarm secrets are immutable.

1. Rotate the credential in its owning system first, retaining the old
   credential until the backend has passed readiness with the new revision.
2. Build a temporary secret source with the complete new revision, provision it,
   set `RAFAGA_SECRETS_REVISION` in `backend.env`, and redeploy the same
   immutable backend image.
3. Verify readiness, a manager authentication flow, R2 media access, and the
   protected operations endpoint.
4. Revoke the old external credential and remove old Swarm secrets only after
   no service references their revision. Record the date, owner, revision, and
   verification outcome in the private operations log.

| Secret class | Rotation trigger | Required verification |
| --- | --- | --- |
| PostgreSQL runtime credentials | Scheduled 90-day rotation or suspected exposure | Readiness and tenant-isolated read/write check |
| Redis password | Scheduled 90-day rotation or suspected exposure | Cache degradation/recovery and rate-limit check |
| Keycloak service credentials | Scheduled 90-day rotation or suspected exposure | Manager login and user provisioning |
| R2 access key | Scheduled 90-day rotation or suspected exposure | Upload initiation, confirmation, and public final asset |
| Registration token | Scheduled 90-day rotation or suspected exposure | Authorized registration and rejection with prior token |
| Cloudflare API token | Scheduled 90-day rotation or suspected exposure | Domain/R2 operation only after those tasks exist |
| Internal Traefik token | Scheduled 90-day rotation or suspected exposure | Tenant Directory access only after the Traefik task exists |

Never put secret values in source control, stack YAML, tenant records, logs,
health responses, CI command lines, or API responses.
