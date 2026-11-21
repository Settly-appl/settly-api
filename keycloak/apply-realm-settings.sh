#!/bin/sh
# Applies realm settings that realm-export.json cannot deliver to an existing
# realm: `--import-realm` skips a realm that already exists, so anything added
# to the export after the first boot (like the login theme) never reaches a
# running install. This pushes those settings through the Admin API instead.
#
# Idempotent; runs on every deploy (ci-cd.yml) and can be run locally:
#   keycloak/apply-realm-settings.sh                          # dev
#   keycloak/apply-realm-settings.sh docker-compose.prod.yml  # prod
#
# Uses the bootstrap admin the container was started with
# (KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD), so no secret is passed here.
set -eu

COMPOSE_FILE=${1:-docker-compose.yml}

docker compose -f "$COMPOSE_FILE" exec -T keycloak bash -eu -c '
kcadm() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }

# Keycloak may still be booting (it was just started or restarted): retry the
# login until it answers, but give up at once on bad credentials.
tries=0
until out=$(kcadm config credentials --server http://localhost:8080/auth --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" 2>&1); do
  case "$out" in
    *invalid_grant*|*"Invalid user credentials"*)
      echo "Keycloak admin login rejected: $out" >&2; exit 1 ;;
  esac
  tries=$((tries + 1))
  if [ "$tries" -ge 90 ]; then echo "Keycloak did not come up: $out" >&2; exit 1; fi
  sleep 2
done

kcadm update realms/settly \
  -s loginTheme=settly \
  -s internationalizationEnabled=true \
  -s "supportedLocales=[\"pl\",\"en\"]" \
  -s defaultLocale=pl

echo "Realm settly: loginTheme=settly, locales pl/en (default pl)"
'
