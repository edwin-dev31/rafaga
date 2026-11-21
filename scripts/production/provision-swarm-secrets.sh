#!/bin/sh
set -eu

usage() {
  echo "Usage: $0 <secret-source.env> <revision>" >&2
  exit 64
}

[ "$#" -eq 2 ] || usage

secret_source=$1
revision=$2

[ -r "$secret_source" ] || {
  echo "Secret source file is not readable" >&2
  exit 66
}

case "$revision" in
  *[!A-Za-z0-9._-]*|'')
    echo "Revision must use only letters, digits, dots, underscores, or hyphens" >&2
    exit 64
    ;;
esac

set -a
. "$secret_source"
set +a

require_secret() {
  environment_name=$1
  value=$(printenv "$environment_name" || true)

  [ -n "$value" ] || {
    echo "$environment_name is missing from the secret source" >&2
    exit 65
  }
}

create_secret() {
  environment_name=$1
  secret_suffix=$2
  value=$(printenv "$environment_name")

  secret_name="rafaga_${secret_suffix}_${revision}"
  if docker secret inspect "$secret_name" >/dev/null 2>&1; then
    echo "Secret revision already exists: $secret_name" >&2
    exit 65
  fi

  printf '%s' "$value" | docker secret create "$secret_name" - >/dev/null
  echo "Created $secret_name"
}

create_optional_secret() {
  environment_name=$1
  secret_suffix=$2
  value=$(printenv "$environment_name" || true)

  if [ -z "$value" ]; then
    echo "Skipping optional secret $environment_name"
    return
  fi

  create_secret "$environment_name" "$secret_suffix"
}

for required_secret in \
  DB_SHARED_URL DB_SHARED_USER DB_SHARED_PASSWORD REDIS_PASSWORD \
  KEYCLOAK_CLIENT_SECRET KEYCLOAK_ADMIN_CLIENT_SECRET \
  RAFAGA_IDENTITY_REGISTRATION_TOKEN MEDIA_R2_ACCOUNT_ID \
  MEDIA_R2_ACCESS_KEY_ID MEDIA_R2_SECRET_ACCESS_KEY; do
  require_secret "$required_secret"
done

create_secret DB_SHARED_URL db_shared_url
create_secret DB_SHARED_USER db_shared_user
create_secret DB_SHARED_PASSWORD db_shared_password
create_secret REDIS_PASSWORD redis_password
create_secret KEYCLOAK_CLIENT_SECRET keycloak_client_secret
create_secret KEYCLOAK_ADMIN_CLIENT_SECRET keycloak_admin_client_secret
create_secret RAFAGA_IDENTITY_REGISTRATION_TOKEN identity_registration_token
create_secret MEDIA_R2_ACCOUNT_ID media_r2_account_id
create_secret MEDIA_R2_ACCESS_KEY_ID media_r2_access_key_id
create_secret MEDIA_R2_SECRET_ACCESS_KEY media_r2_secret_access_key
create_optional_secret CLOUDFLARE_API_TOKEN cloudflare_api_token
create_optional_secret TRAEFIK_INTERNAL_TOKEN traefik_internal_token
