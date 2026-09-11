#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT=${1:?usage: deploy-oracle-release.sh <uat|prod> <tested-server.jar> <release-version> [expected-sha256] [expected-minimum-desktop]}
JAR=${2:?path to tested DSE ERP server JAR}
VERSION=${3:?release version}
EXPECTED_SHA=${4:-}
EXPECTED_MINIMUM_DESKTOP=${5:-10.0.1}

case "$ENVIRONMENT" in
  uat|prod) ;;
  *) echo 'environment must be uat or prod' >&2; exit 2 ;;
esac

[[ -s "$JAR" ]] || { echo "Server JAR missing/empty: $JAR" >&2; exit 2; }
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid release version: $VERSION" >&2; exit 2; }
[[ "$EXPECTED_MINIMUM_DESKTOP" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid minimum supported desktop version: $EXPECTED_MINIMUM_DESKTOP" >&2; exit 2; }

EXPECTED_ENV=$(printf '%s' "$ENVIRONMENT" | tr '[:lower:]' '[:upper:]')
ENV_FILE="/etc/dse-erp/${ENVIRONMENT}.env"
PASSWORD_FILE="/etc/dse-erp/${ENVIRONMENT}-db-password"
BASE="/srv/dse-erp/${ENVIRONMENT}"
RELEASES="$BASE/releases"
RELEASE="$RELEASES/$VERSION"
WORKSPACE_DEFAULT="$BASE/workspace"
SERVICE="dse-erp-${ENVIRONMENT}"

if ! sudo -n test -r "$ENV_FILE"; then
  echo "Environment file is missing or not readable through deployment sudo: $ENV_FILE" >&2
  exit 2
fi
if ! sudo -n test -r "$PASSWORD_FILE"; then
  echo "Database password file is missing or not readable through deployment sudo: $PASSWORD_FILE" >&2
  exit 2
fi

# Oracle keeps deployment configuration root-owned. GitHub connects as the
# restricted deployment account, so read the trusted env file through non-interactive
# sudo -n instead of requiring that account to have direct filesystem read permission.
ENV_CONTENT=$(sudo -n cat "$ENV_FILE") || {
  echo "Unable to read deployment environment through sudo: $ENV_FILE" >&2
  exit 2
}
# shellcheck disable=SC1090
set -a
source /dev/stdin <<< "$ENV_CONTENT"
set +a
unset ENV_CONTENT

[[ "${DSE_DEPLOYMENT_ENVIRONMENT:-}" == "$EXPECTED_ENV" ]] || {
  echo "Environment safety mismatch: $ENV_FILE must declare DSE_DEPLOYMENT_ENVIRONMENT=$EXPECTED_ENV" >&2
  exit 2
}
[[ -n "${DSE_EXPECTED_DATABASE:-}" ]] || { echo 'DSE_EXPECTED_DATABASE is required' >&2; exit 2; }
[[ -n "${DSE_DB_USERNAME:-}" ]] || { echo 'DSE_DB_USERNAME is required' >&2; exit 2; }

WORKSPACE=${DSE_WORKSPACE_PATH:-$WORKSPACE_DEFAULT}
POSTGRES_HOME=${DSE_POSTGRES_HOME:-}
if [[ -n "$POSTGRES_HOME" ]]; then
  PG_DUMP="$POSTGRES_HOME/bin/pg_dump"
  PG_RESTORE="$POSTGRES_HOME/bin/pg_restore"
else
  PG_DUMP=$(command -v pg_dump || true)
  PG_RESTORE=$(command -v pg_restore || true)
fi
[[ -x "$PG_DUMP" ]] || { echo "pg_dump not found/executable: $PG_DUMP" >&2; exit 2; }
[[ -x "$PG_RESTORE" ]] || { echo "pg_restore not found/executable: $PG_RESTORE" >&2; exit 2; }

ACTUAL_SHA=$(sha256sum "$JAR" | awk '{print $1}')
if [[ -n "$EXPECTED_SHA" && "$ACTUAL_SHA" != "${EXPECTED_SHA,,}" ]]; then
  echo "Server JAR SHA-256 mismatch. Expected ${EXPECTED_SHA,,}; got $ACTUAL_SHA" >&2
  exit 2
fi

echo "Release artifact SHA-256 verified: $ACTUAL_SHA"

PREVIOUS=$(readlink -f "$BASE/current" 2>/dev/null || true)
PREVIOUS_VERSION=''
if [[ -n "$PREVIOUS" ]]; then
  PREVIOUS_VERSION=$(basename "$PREVIOUS")
fi

health_url() {
  local port=${DSE_SERVER_PORT:-8081}
  printf 'http://127.0.0.1:%s/api/runtime/health' "$port"
}

validate_health() {
  local expected_version=$1
  local body=$2
  local enforce_floor=${3:-false}
  python3 - "$expected_version" "$EXPECTED_ENV" "$DSE_EXPECTED_DATABASE" "$EXPECTED_MINIMUM_DESKTOP" "$enforce_floor" "$body" <<'PY'
import json,sys
version,environment,database,minimum,enforce_floor,body=sys.argv[1:]
r=json.loads(body)
ok=(r.get('ready') is True
    and r.get('version')==version
    and r.get('buildRevision')==version
    and r.get('environment')==environment
    and r.get('databaseName')==database)
if enforce_floor.lower() == 'true':
    ok = ok and r.get('minimumSupportedDesktopVersion') == minimum
    try:
        ok = ok and tuple(map(int, minimum.split('.'))) <= tuple(map(int, version.split('.')))
    except Exception:
        ok = False
raise SystemExit(0 if ok else 1)
PY
}

wait_for_health() {
  local expected_version=$1
  local enforce_floor=${2:-true}
  local url
  url=$(health_url)
  local body=''
  for attempt in $(seq 1 45); do
    if body=$(curl --fail --silent --show-error --max-time 3 "$url" 2>/dev/null); then
      if validate_health "$expected_version" "$body" "$enforce_floor"; then
        printf '%s' "$body"
        return 0
      fi
    fi
    echo "Waiting for $EXPECTED_ENV $expected_version health... attempt $attempt/45" >&2
    sleep 2
  done
  return 1
}

if [[ -n "$PREVIOUS" ]]; then
  CURRENT_BODY=$(curl --fail --silent --show-error --max-time 5 "$(health_url)") || {
    echo 'Current server is not healthy; refusing to deploy on top of an unhealthy environment.' >&2
    exit 1
  }
  if ! validate_health "$PREVIOUS_VERSION" "$CURRENT_BODY" false; then
    echo "Current release/environment/database health does not match $PREVIOUS_VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE." >&2
    exit 1
  fi
  echo "Current release verified before deployment: $PREVIOUS_VERSION"
  if [[ "$PREVIOUS_VERSION" == "$VERSION" ]]; then
    CURRENT_JAR="$PREVIOUS/server.jar"
    [[ -s "$CURRENT_JAR" ]] || { echo "Current same-version server.jar is missing: $CURRENT_JAR" >&2; exit 1; }
    CURRENT_SHA=$(sudo -n sha256sum "$CURRENT_JAR" | awk '{print $1}')
    if [[ "$CURRENT_SHA" == "$ACTUAL_SHA" ]]; then
      echo "Release $VERSION is already deployed with the exact requested artifact; no restart required."
      echo "$CURRENT_BODY"
      exit 0
    fi
    echo "Refusing to replace a running $VERSION release with different JAR bytes. Create a new release version instead." >&2
    exit 1
  fi
fi

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP_DIR="$WORKSPACE/Backups/PreUpgrade"
PRE_BACKUP="$BACKUP_DIR/DSE-ERP-${EXPECTED_ENV}-before-${VERSION}-${STAMP}.pgbackup"

sudo -n install -d -o dseerp -g dseerp "$RELEASES" "$RELEASE" "$BACKUP_DIR"

DB_PASSWORD=$(sudo -n cat "$PASSWORD_FILE")
cleanup_password() { DB_PASSWORD=''; unset DB_PASSWORD || true; }
trap cleanup_password EXIT

echo "Creating validated pre-upgrade backup: $PRE_BACKUP"
sudo -n -u dseerp env PGPASSWORD="$DB_PASSWORD" "$PG_DUMP" \
  --format=custom --no-owner --no-privileges \
  --host=127.0.0.1 \
  --username="$DSE_DB_USERNAME" \
  --dbname="$DSE_EXPECTED_DATABASE" \
  --file="$PRE_BACKUP"
sudo -n -u dseerp "$PG_RESTORE" --list "$PRE_BACKUP" >/dev/null
sudo -n -u dseerp test -s "$PRE_BACKUP" || { echo 'Pre-upgrade database backup is empty or inaccessible to dseerp' >&2; exit 1; }
cleanup_password
trap - EXIT

echo "Installing $VERSION into $RELEASE"
DEST_JAR="$RELEASE/DSE-ERP-${VERSION}-SERVER.jar"
sudo -n install -o dseerp -g dseerp -m 0640 "$JAR" "$DEST_JAR"
sudo -n ln -sfn "DSE-ERP-${VERSION}-SERVER.jar" "$RELEASE/server.jar"
INSTALLED_SHA=$(sudo -n sha256sum "$DEST_JAR" | awk '{print $1}')
[[ "$INSTALLED_SHA" == "$ACTUAL_SHA" ]] || { echo 'Installed server JAR checksum mismatch' >&2; exit 1; }

if [[ -n "$PREVIOUS" ]]; then
  printf '%s\n' "$PREVIOUS" | sudo -n tee "$BASE/previous-release" >/dev/null
fi

rollback_binary() {
  local reason=$1
  echo "Deployment failed: $reason" >&2
  echo 'Rolling server binary back to the previous release.' >&2
  sudo -n systemctl stop "$SERVICE" 2>/dev/null || true
  if [[ -n "$PREVIOUS" && -d "$PREVIOUS" ]]; then
    sudo -n ln -sfn "$PREVIOUS" "$BASE/current"
    sudo -n systemctl start "$SERVICE"
    if ! ROLLBACK_BODY=$(wait_for_health "$PREVIOUS_VERSION" false); then
      echo 'CRITICAL: previous server release did not recover cleanly.' >&2
      echo "Pre-upgrade database backup: $PRE_BACKUP" >&2
      exit 1
    fi
    echo "Binary rollback verified: $PREVIOUS_VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE" >&2
  else
    echo 'No previous release was available for binary rollback.' >&2
  fi
  echo "Pre-upgrade database backup preserved at: $PRE_BACKUP" >&2
  echo 'If an incompatible database migration was applied, restore this backup before reopening user access.' >&2
  return 1
}

sudo -n systemctl stop "$SERVICE" 2>/dev/null || true
sudo -n ln -sfn "$RELEASE" "$BASE/current"
if ! sudo -n systemctl start "$SERVICE"; then
  rollback_binary 'systemd could not start the new release'
  exit 1
fi

if ! NEW_BODY=$(wait_for_health "$VERSION"); then
  echo 'Recent service log:' >&2
  sudo -n journalctl -u "$SERVICE" -n 80 --no-pager >&2 || true
  rollback_binary 'health verification failed'
  exit 1
fi

sudo -n systemctl is-active --quiet "$SERVICE" || {
  rollback_binary 'service is not active after a successful health response'
  exit 1
}

echo "$NEW_BODY"
echo "Deployment verified: $VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE / minimum desktop $EXPECTED_MINIMUM_DESKTOP"
echo "Current release: $(readlink -f "$BASE/current")"
echo "Previous release: ${PREVIOUS:-none}"
echo "Pre-upgrade backup: $PRE_BACKUP"
echo "Server JAR SHA-256: $ACTUAL_SHA"
