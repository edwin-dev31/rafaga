#!/bin/sh
set -eu

: "${POSTGRES_HOST:?POSTGRES_HOST is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${DB_SHARED_USER:?DB_SHARED_USER is required}"
: "${DB_SHARED_PASSWORD:?DB_SHARED_PASSWORD is required}"

export PGPASSWORD="$POSTGRES_PASSWORD"

psql -X -v ON_ERROR_STOP=1 \
    -h "$POSTGRES_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v runtime_user="$DB_SHARED_USER" \
    -v runtime_password="$DB_SHARED_PASSWORD" \
    -v admin_user="$POSTGRES_USER" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L',
    :'runtime_user', :'runtime_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'runtime_user')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L',
    :'runtime_user', :'runtime_password')
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'runtime_user')
\gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'runtime_user')
\gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'runtime_user')
\gexec
SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'runtime_user')
\gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
    || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
    :'admin_user', :'runtime_user')
\gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
    || 'GRANT USAGE, SELECT ON SEQUENCES TO %I',
    :'admin_user', :'runtime_user')
\gexec
SQL
