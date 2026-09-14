#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT=${1:?usage: deploy-oracle-release.sh <uat|prod> <tested-server.jar> <release-version> [expected-sha256] [expected-minimum-desktop] [expected-minimum-android] [expected-latest-android] [expected-minimum-ios] [expected-latest-ios]}
JAR=${2:?path to tested DSE ERP server JAR}
VERSION=${3:?release version}
EXPECTED_SHA=${4:-}
EXPECTED_MINIMUM_DESKTOP=${5:-10.0.4}
# Mobile versions supplied by the ERP release are bootstrap fallbacks only.
# Once an environment is live, Android/iOS compatibility policy is owned by
# the independent mobile release workflows and must survive ERP upgrades.
EXPECTED_MINIMUM_ANDROID=${6:-1.2.3}
EXPECTED_LATEST_ANDROID=${7:-1.2.3}
EXPECTED_MINIMUM_IOS=${8:-1.2.3}
EXPECTED_LATEST_IOS=${9:-1.2.3}

case "$ENVIRONMENT" in
  uat|prod) ;;
  *) echo 'environment must be uat or prod' >&2; exit 2 ;;
esac

[[ -s "$JAR" ]] || { echo "Server JAR missing/empty: $JAR" >&2; exit 2; }
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid release version: $VERSION" >&2; exit 2; }
[[ "$EXPECTED_MINIMUM_DESKTOP" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid minimum supported desktop version: $EXPECTED_MINIMUM_DESKTOP" >&2; exit 2; }
for mobile_version in "$EXPECTED_MINIMUM_ANDROID" "$EXPECTED_LATEST_ANDROID" "$EXPECTED_MINIMUM_IOS" "$EXPECTED_LATEST_IOS"; do
  [[ "$mobile_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid mobile compatibility version: $mobile_version" >&2; exit 2; }
done

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

# Android/iOS policy is environment-owned. The mobile deployment workflow
# updates these values in /etc/dse-erp/<env>.env, so ERP deployment must use
# the protected environment file as the source of truth even on the first
# managed PROD deploy (when /srv/dse-erp/<env>/current may not exist yet).
# Release/POM values remain bootstrap fallbacks only when an environment value
# has not been configured.
EFFECTIVE_MINIMUM_ANDROID="${DSE_MINIMUM_SUPPORTED_ANDROID_VERSION:-$EXPECTED_MINIMUM_ANDROID}"
EFFECTIVE_LATEST_ANDROID="${DSE_LATEST_ANDROID_VERSION:-$EXPECTED_LATEST_ANDROID}"
EFFECTIVE_MINIMUM_IOS="${DSE_MINIMUM_SUPPORTED_IOS_VERSION:-$EXPECTED_MINIMUM_IOS}"
EFFECTIVE_LATEST_IOS="${DSE_LATEST_IOS_VERSION:-$EXPECTED_LATEST_IOS}"

for mobile_version in "$EFFECTIVE_MINIMUM_ANDROID" "$EFFECTIVE_LATEST_ANDROID" "$EFFECTIVE_MINIMUM_IOS" "$EFFECTIVE_LATEST_IOS"; do
  [[ "$mobile_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid environment-owned mobile compatibility version: $mobile_version" >&2; exit 2; }
done
python3 - "$EFFECTIVE_MINIMUM_ANDROID" "$EFFECTIVE_LATEST_ANDROID" "$EFFECTIVE_MINIMUM_IOS" "$EFFECTIVE_LATEST_IOS" <<'PY_MOBILE_POLICY'
import sys
min_android,latest_android,min_ios,latest_ios=sys.argv[1:]
parse=lambda x: tuple(map(int,x.split('.')))
if parse(min_android) > parse(latest_android):
    raise SystemExit(f'Invalid environment-owned Android compatibility range: {min_android}..{latest_android}')
if parse(min_ios) > parse(latest_ios):
    raise SystemExit(f'Invalid environment-owned iOS compatibility range: {min_ios}..{latest_ios}')
PY_MOBILE_POLICY
echo "MOBILE_POLICY_PRESERVED source=environment-file Android=$EFFECTIVE_MINIMUM_ANDROID..$EFFECTIVE_LATEST_ANDROID iOS=$EFFECTIVE_MINIMUM_IOS..$EFFECTIVE_LATEST_IOS"

health_url() {
  local port=${DSE_SERVER_PORT:-8081}
  printf 'http://127.0.0.1:%s/api/runtime/health' "$port"
}

validate_health() {
  local expected_version=$1
  local body=$2
  local enforce_floor=${3:-false}
  python3 - "$expected_version" "$EXPECTED_ENV" "$DSE_EXPECTED_DATABASE" "$EXPECTED_MINIMUM_DESKTOP" "$EFFECTIVE_MINIMUM_ANDROID" "$EFFECTIVE_LATEST_ANDROID" "$EFFECTIVE_MINIMUM_IOS" "$EFFECTIVE_LATEST_IOS" "$enforce_floor" "$body" <<'PY'
import json,re,sys
version,environment,database,min_desktop,min_android,latest_android,min_ios,latest_ios,enforce_floor,body=sys.argv[1:]
r=json.loads(body)
semver=re.compile(r'^\d+\.\d+\.\d+$')
def parsed(value):
    if not isinstance(value,str) or not semver.fullmatch(value):
        raise ValueError(value)
    return tuple(map(int,value.split('.')))
ok=(r.get('ready') is True
    and r.get('version')==version
    and r.get('buildRevision')==version
    and r.get('environment')==environment
    and r.get('databaseName')==database)
if enforce_floor.lower() == 'true':
    ok = (ok
          and r.get('minimumSupportedDesktopVersion') == min_desktop
          and r.get('latestDesktopVersion') == version
          and r.get('minimumSupportedAndroidVersion') == min_android
          and r.get('latestAndroidVersion') == latest_android
          and r.get('minimumSupportedIosVersion') == min_ios
          and r.get('latestIosVersion') == latest_ios)
    try:
        ok = ok and parsed(min_desktop) <= parsed(version)
        ok = ok and parsed(min_android) <= parsed(latest_android)
        ok = ok and parsed(min_ios) <= parsed(latest_ios)
    except Exception:
        ok = False
raise SystemExit(0 if ok else 1)
PY
}

capture_live_mobile_policy() {
  local body=$1
  python3 - "$body" <<'PY'
import json,re,sys
r=json.loads(sys.argv[1])
keys=('minimumSupportedAndroidVersion','latestAndroidVersion','minimumSupportedIosVersion','latestIosVersion')
semver=re.compile(r'^\d+\.\d+\.\d+$')
values=[]
for key in keys:
    value=r.get(key)
    if not isinstance(value,str) or not semver.fullmatch(value):
        raise SystemExit(f'Invalid live mobile compatibility value {key}={value!r}')
    values.append(value)
parse=lambda x: tuple(map(int,x.split('.')))
if parse(values[0]) > parse(values[1]):
    raise SystemExit(f'Invalid live Android compatibility range: {values[0]}..{values[1]}')
if parse(values[2]) > parse(values[3]):
    raise SystemExit(f'Invalid live iOS compatibility range: {values[2]}..{values[3]}')
print('|'.join(values))
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
  # A failed first managed deployment can leave `current` pointing at a valid
  # release while the service is stopped. Recover that current release first
  # so it becomes a verified rollback target before attempting the next deploy.
  CURRENT_BODY=$(curl --fail --silent --show-error --max-time 5 "$(health_url)" 2>/dev/null || true)
  if [[ -z "$CURRENT_BODY" ]]; then
    echo "Current managed release $PREVIOUS_VERSION is not responding; attempting safe service recovery before deployment." >&2
    sudo -n systemctl start "$SERVICE" >/dev/null 2>&1 || true
    CURRENT_BODY=$(wait_for_health "$PREVIOUS_VERSION" true || true)
  fi
  if [[ -z "$CURRENT_BODY" ]] || ! validate_health "$PREVIOUS_VERSION" "$CURRENT_BODY" true; then
    echo "Current managed release could not be verified as a rollback target: $PREVIOUS_VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE." >&2
    echo 'Refusing to replace it until the current release or environment configuration is healthy.' >&2
    exit 1
  fi
  LIVE_MOBILE_POLICY=$(capture_live_mobile_policy "$CURRENT_BODY") || {
    echo 'Current live mobile compatibility policy is invalid; refusing to deploy.' >&2
    exit 1
  }
  EXPECTED_ENV_MOBILE_POLICY="$EFFECTIVE_MINIMUM_ANDROID|$EFFECTIVE_LATEST_ANDROID|$EFFECTIVE_MINIMUM_IOS|$EFFECTIVE_LATEST_IOS"
  if [[ "$LIVE_MOBILE_POLICY" != "$EXPECTED_ENV_MOBILE_POLICY" ]]; then
    echo "Current live mobile policy does not match $ENV_FILE; refusing to let ERP deployment overwrite mobile-owned policy." >&2
    echo "environment=$EXPECTED_ENV_MOBILE_POLICY live=$LIVE_MOBILE_POLICY" >&2
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
else
  # First managed deploy: there is no versioned rollback symlink yet. Mobile
  # policy still comes from the protected environment file, not stale POM data.
  # If a legacy service is currently healthy, verify that it advertises the
  # same environment-owned mobile policy before replacing it.
  CURRENT_BODY=$(curl --fail --silent --show-error --max-time 5 "$(health_url)" 2>/dev/null || true)
  if [[ -n "$CURRENT_BODY" ]]; then
    LIVE_MOBILE_POLICY=$(capture_live_mobile_policy "$CURRENT_BODY") || {
      echo 'Current live mobile compatibility policy is invalid; refusing first managed deployment.' >&2
      exit 1
    }
    EXPECTED_ENV_MOBILE_POLICY="$EFFECTIVE_MINIMUM_ANDROID|$EFFECTIVE_LATEST_ANDROID|$EFFECTIVE_MINIMUM_IOS|$EFFECTIVE_LATEST_IOS"
    if [[ "$LIVE_MOBILE_POLICY" != "$EXPECTED_ENV_MOBILE_POLICY" ]]; then
      echo "Legacy live mobile policy does not match $ENV_FILE; refusing first managed deployment." >&2
      echo "environment=$EXPECTED_ENV_MOBILE_POLICY live=$LIVE_MOBILE_POLICY" >&2
      exit 1
    fi
    echo "First managed deployment verified environment-owned mobile policy from the running server."
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
echo "Deployment verified: $VERSION / $EXPECTED_ENV / $DSE_EXPECTED_DATABASE / desktop >= $EXPECTED_MINIMUM_DESKTOP / Android $EFFECTIVE_MINIMUM_ANDROID..$EFFECTIVE_LATEST_ANDROID / iOS $EFFECTIVE_MINIMUM_IOS..$EFFECTIVE_LATEST_IOS"
echo "Current release: $(readlink -f "$BASE/current")"
echo "Previous release: ${PREVIOUS:-none}"
echo "Pre-upgrade backup: $PRE_BACKUP"
echo "Server JAR SHA-256: $ACTUAL_SHA"
