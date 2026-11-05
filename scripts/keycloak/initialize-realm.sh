#!/bin/sh
set -eu

exec /opt/keycloak/bin/kc.sh start-dev --import-realm
