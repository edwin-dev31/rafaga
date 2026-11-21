#!/bin/sh
set -eu

load_secret() {
  secret_name="$1"
  environment_name="$2"
  secret_path="/run/secrets/$secret_name"

  if [ -z "$(printenv "$environment_name" || true)" ] && [ -r "$secret_path" ]; then
    export "$environment_name=$(cat "$secret_path")"
  fi
}

# Secret names intentionally contain no tenant data. The image can also run
# outside Swarm when all values are supplied as normal environment variables.
load_secret db_shared_url DB_SHARED_URL
load_secret db_shared_user DB_SHARED_USER
load_secret db_shared_password DB_SHARED_PASSWORD
load_secret redis_password REDIS_PASSWORD
load_secret keycloak_client_secret KEYCLOAK_CLIENT_SECRET
load_secret keycloak_admin_client_secret KEYCLOAK_ADMIN_CLIENT_SECRET
load_secret identity_registration_token RAFAGA_IDENTITY_REGISTRATION_TOKEN
load_secret media_r2_account_id MEDIA_R2_ACCOUNT_ID
load_secret media_r2_access_key_id MEDIA_R2_ACCESS_KEY_ID
load_secret media_r2_secret_access_key MEDIA_R2_SECRET_ACCESS_KEY

exec java -XX:MaxRAMPercentage=75 -jar /app/rafaga-backend.jar
