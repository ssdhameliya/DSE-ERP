#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT=${1:?usage: deploy-release.sh <uat|prod> <tested-server.jar> <release-version>}
JAR=${2:?path to tested dse-erp-server.jar}
VERSION=${3:?release version}
EXPECTED_MINIMUM_DESKTOP=${4:-10.0.4}
case "$ENVIRONMENT" in uat|prod) ;; *) echo 'environment must be uat or prod' >&2; exit 2;; esac
[[ -s "$JAR" ]] || { echo "Server JAR missing/empty: $JAR" >&2; exit 2; }

ENV_FILE="/etc/dse-erp/$ENVIRONMENT.env"
[[ -r "$ENV_FILE" ]] || { echo "Missing environment file: $ENV_FILE" >&2; exit 2; }
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a

EXPECTED_ENV=$(printf '%s' "$ENVIRONMENT" | tr '[:lower:]' '[:upper:]')
[[ "${DSE_DEPLOYMENT_ENVIRONMENT:-}" == "$EXPECTED_ENV" ]] || {
  echo "Environment safety mismatch: $ENV_FILE must declare DSE_DEPLOYMENT_ENVIRONMENT=$EXPECTED_ENV" >&2; exit 2;
}
[[ -n "${DSE_EXPECTED_DATABASE:-}" ]] || { echo 'DSE_EXPECTED_DATABASE is required' >&2; exit 2; }
[[ -n "${DSE_DB_URL:-}" && -n "${DSE_DB_USERNAME:-}" && -n "${DSE_DB_PASSWORD:-}" ]] || {
  echo 'Database URL/user/password must be configured' >&2; exit 2;
}

DB_URI=${DSE_DB_URL#jdbc:}
BASE="/opt/dse-erp/$ENVIRONMENT"
RELEASE="$BASE/releases/$VERSION"
SERVICE="dse-erp@$ENVIRONMENT"
PREVIOUS=$(readlink -f "$BASE/current" 2>/dev/null || true)
BACKUP_DIR="/srv/dse-erp/$ENVIRONMENT/Backups/PreUpgrade"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
PRE_BACKUP="$BACKUP_DIR/pre-${VERSION}-${STAMP}.pgbackup"

# A deployment backup is independent from the normal weekly/keep-2 rotation.
sudo install -d -o dseerp -g dseerp "$RELEASE" "$BACKUP_DIR"
echo "Creating validated pre-upgrade backup: $PRE_BACKUP"
sudo -u dseerp env PGPASSWORD="$DSE_DB_PASSWORD" pg_dump \
  --format=custom --no-owner --no-privileges \
  --username="$DSE_DB_USERNAME" --file="$PRE_BACKUP" "$DB_URI"
sudo -u dseerp pg_restore --list "$PRE_BACKUP" >/dev/null
[[ -s "$PRE_BACKUP" ]] || { echo 'Pre-upgrade backup is empty' >&2; exit 1; }

sudo install -o dseerp -g dseerp -m 0644 "$JAR" "$RELEASE/dse-erp-server.jar"
sudo systemctl stop "$SERVICE" 2>/dev/null || true
sudo ln -sfn "$RELEASE" "$BASE/current"

rollback_binary() {
  echo 'Deployment failed; restoring previous server release symlink.' >&2
  sudo systemctl stop "$SERVICE" 2>/dev/null || true
  if [[ -n "$PREVIOUS" && -d "$PREVIOUS" ]]; then
    sudo ln -sfn "$PREVIOUS" "$BASE/current"
    sudo systemctl start "$SERVICE" || true
  fi
  echo "Pre-upgrade database backup preserved at: $PRE_BACKUP" >&2
  echo 'If the new release applied an incompatible schema migration, restore that backup before reopening user access.' >&2
}
trap rollback_binary ERR

sudo systemctl start "$SERVICE"

PORT=${DSE_SERVER_PORT:-8080}
HEALTH="http://127.0.0.1:${PORT}/api/runtime/health"
ready=''
for _ in $(seq 1 30); do
  if body=$(curl --fail --silent --show-error --max-time 3 "$HEALTH" 2>/dev/null); then
    if python3 - "$VERSION" "$EXPECTED_ENV" "$DSE_EXPECTED_DATABASE" "$EXPECTED_MINIMUM_DESKTOP" "$body" <<'PY'
import json,sys
version,environment,database,minimum,body=sys.argv[1:]
r=json.loads(body)
ok=(r.get('ready') is True and r.get('version')==version and r.get('buildRevision')==version
    and r.get('environment')==environment and r.get('databaseName')==database
    and r.get('minimumSupportedDesktopVersion')==minimum)
raise SystemExit(0 if ok else 1)
PY
    then ready=1; break; fi
  fi
  sleep 2
done
[[ "$ready" == 1 ]] || { echo "Health verification failed: $HEALTH" >&2; false; }

trap - ERR
echo "Deployment verified: $VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE / minimum desktop $EXPECTED_MINIMUM_DESKTOP"
echo "Pre-upgrade backup: $PRE_BACKUP"
