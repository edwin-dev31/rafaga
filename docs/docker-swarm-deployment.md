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

Load the required production values from the platform secret store into the
deploy shell. They must include the database, Redis, Keycloak, R2, CORS,
registration-token and trusted-proxy settings documented in `.env.example`.

```bash
export RAFAGA_BACKEND_IMAGE=ghcr.io/<owner>/rafaga-backend:<immutable-tag>
export SPRING_PROFILES_ACTIVE=production
# Export the remaining production variables from the secret manager here.
docker stack deploy --with-registry-auth -c deploy/swarm/rafaga-backend-stack.yaml rafaga
docker service ps rafaga_backend
curl -fsS http://<private-backend-address>:3117/actuator/health/readiness
```

The production configuration validator prevents startup with missing or unsafe
settings. The stack passes the sourced variables into the backend container;
therefore, the protected `/opt/rafaga/backend.env` must contain every required
production value. Never use `latest`, commit a `.env` file, or place
credentials in the stack YAML.

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
The VPS keeps all Spring production values in `/opt/rafaga/backend.env` with
owner-only permissions. The workflow uploads only the public stack definition,
selects an immutable GHCR tag, and sources that protected environment file on
the VPS before executing `docker stack deploy`.
