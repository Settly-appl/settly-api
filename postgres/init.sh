#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE USER keycloak WITH PASSWORD '$KEYCLOAK_DB_PASSWORD';
    CREATE DATABASE keycloak OWNER keycloak;
    CREATE USER "settly-user" WITH PASSWORD '$SETTLY_DB_PASSWORD';
    CREATE DATABASE "settly-db" OWNER "settly-user";
EOSQL
