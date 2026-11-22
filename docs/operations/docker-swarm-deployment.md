# Docker Swarm backend deployment

This stack deploys one backend replica only. PostgreSQL, Redis, Keycloak and
the future Gateway remain independent services; the backend has no published
host port and is reached through the external `rafaga_edge` overlay network.

## One-time manager setup

```bash
docker swarm init
docker network create --driver overlay --attachable rafaga_edge
docker network create --driver overlay --attachable rafaga_data
```

Attach the Gateway to `rafaga_edge`; attach the backend and its private data
dependencies to `rafaga_data`. Do not expose PostgreSQL, Redis, Keycloak admin
or the backend directly on public host ports.

## Deploy or update

Follow the [production operations contract](production-operations-contract.md)
and install its two protected files on the Swarm manager: configuration in
`/opt/rafaga/backend.env` and the temporary secret source in
`/opt/rafaga/secret-source.env`. The latter is consumed to create immutable
Swarm Secrets and is then removed from the manager.

```bash
scripts/production/provision-swarm-secrets.sh /opt/rafaga/secret-source.env 20261120-01
rm /opt/rafaga/secret-source.env
scripts/production/deploy-swarm.sh /opt/rafaga/backend.env \
  ghcr.io/<owner>/rafaga-backend:<immutable-tag>
curl -fsS http://<private-backend-address>:3117/actuator/health/readiness
```

The production configuration validator prevents startup with missing or unsafe
settings. The image reads credentials only from `/run/secrets`; the stack and
`backend.env` contain only secret names, revision metadata, and non-secret
configuration. Never use `latest`, commit a `.env` file, or place credentials
in the stack YAML.

## Rollback and recovery

The update policy starts the replacement before stopping the previous replica
and rolls back on a failed update. If manual rollback is needed, redeploy the
previous immutable image tag. For a single replica, schedule a maintenance
window: a successful start-first update still needs enough host memory for both
containers briefly.

Before adding replicas, reduce per-replica Hikari capacity as needed and verify
the multi-instance job-lock and load-test tasks in the production-readiness
specification.

## GitHub Actions deployment

`Deploy Swarm` is manual-only and requires a protected GitHub `production`
environment. Configure `VPS_HOST`, `VPS_USER`, `VPS_SSH_PRIVATE_KEY`,
`VPS_KNOWN_HOSTS`, and a read-only `VPS_GHCR_TOKEN` as environment secrets.
The VPS keeps non-secret production configuration in `/opt/rafaga/backend.env`
with owner-only permissions. The workflow uploads the public stack definition
and deployment validator, selects an immutable GHCR tag, verifies the declared
secret revision exists, and then deploys the stack.
