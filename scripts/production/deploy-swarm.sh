#!/bin/sh
set -eu

usage() {
  echo "Usage: $0 <backend.env> <immutable-image-reference>" >&2
  exit 64
}

[ "$#" -eq 2 ] || usage

configuration_file=$1
image_reference=$2
stack_file=${RAFAGA_STACK_FILE:-deploy/swarm/rafaga-backend-stack.yaml}

[ -r "$configuration_file" ] || {
  echo "Backend configuration file is not readable" >&2
  exit 66
}

[ -r "$stack_file" ] || {
  echo "Swarm stack file is not readable: $stack_file" >&2
  exit 66
}

case "$image_reference" in
  *:latest|*:snapshot|*:SNAPSHOT|*' '*|'')
    echo "Use an immutable image reference, never latest or snapshot" >&2
    exit 64
    ;;
esac

if ! printf '%s\n' "$image_reference" | grep -Eq '(@sha256:[0-9a-f]{64}|:[0-9a-f]{40})$'; then
  echo "Image reference must use a SHA-256 digest or a full Git commit SHA tag" >&2
  exit 64
fi

set -a
. "$configuration_file"
set +a

require() {
  variable_name=$1
  value=$(printenv "$variable_name" || true)
  [ -n "$value" ] && [ "$value" != "YYYYMMDD-01" ] || {
    echo "$variable_name must be configured in $configuration_file" >&2
    exit 65
  }
}

require_one_of() {
  for variable_name in "$@"; do
    value=$(printenv "$variable_name" || true)
    if [ -n "$value" ]; then
      return
    fi
  done
  echo "One of these values must be configured in $configuration_file: $*" >&2
  exit 65
}

for required in \
  RAFAGA_SECRETS_REVISION \
  PLATFORM_OPERATIONS_OWNER \
  PLATFORM_ALERT_RECIPIENTS \
  PLATFORM_APPLICATION_VPS \
  PLATFORM_DATA_VPS \
  PLATFORM_IDENTITY_VPS \
  PLATFORM_REDIS_LOCATION \
  PLATFORM_TRAEFIK_PUBLIC_HOST \
  PLATFORM_KEYCLOAK_PUBLIC_HOST \
  KEYCLOAK_JWK_SET_URI \
  KEYCLOAK_ISSUER_URI \
  KEYCLOAK_CLIENT_ID \
  KEYCLOAK_ADMIN_CLIENT_ID \
  RAFAGA_ALLOWED_ORIGINS \
  RAFAGA_TRUSTED_PROXY_CIDRS \
  MEDIA_R2_PUBLIC_BASE_URL; do
  require "$required"
done

require_one_of REDIS_HOST REDIS_CLUSTER_NODES

for secret_suffix in \
  db_shared_url db_shared_user db_shared_password redis_password \
  keycloak_client_secret keycloak_admin_client_secret identity_registration_token \
  media_r2_account_id media_r2_access_key_id media_r2_secret_access_key; do
  docker secret inspect "rafaga_${secret_suffix}_${RAFAGA_SECRETS_REVISION}" >/dev/null
done

export RAFAGA_BACKEND_IMAGE="$image_reference"
docker stack deploy --with-registry-auth -c "$stack_file" rafaga
docker service ps rafaga_backend --no-trunc
