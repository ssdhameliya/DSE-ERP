#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT=${1:?usage: configure-github-update-token.sh <uat|prod>}
case "$ENVIRONMENT" in
  uat|prod) ;;
  *) echo 'environment must be uat or prod' >&2; exit 2 ;;
esac

ENV_FILE="/etc/dse-erp/${ENVIRONMENT}.env"
EXPECTED_ENV=$(printf '%s' "$ENVIRONMENT" | tr '[:lower:]' '[:upper:]')

# The GitHub token is intentionally accepted only on stdin so it never appears
# in the SSH command line, process arguments, repository files or workflow logs.
IFS= read -r TOKEN || true
TOKEN=${TOKEN%$'\r'}
[[ -n "$TOKEN" ]] || { echo 'DSE_GITHUB_UPDATE_TOKEN input is empty.' >&2; exit 2; }
[[ ${#TOKEN} -ge 20 ]] || { echo 'DSE_GITHUB_UPDATE_TOKEN input is unexpectedly short.' >&2; exit 2; }
[[ "$TOKEN" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'DSE_GITHUB_UPDATE_TOKEN contains unexpected characters.' >&2; exit 2; }

sudo -n test -r "$ENV_FILE" || {
  echo "Protected environment file is not readable through deployment sudo: $ENV_FILE" >&2
  exit 2
}

CURRENT=$(mktemp)
UPDATED=$(mktemp)
trap 'rm -f "$CURRENT" "$UPDATED"; unset TOKEN' EXIT
sudo -n cat "$ENV_FILE" > "$CURRENT"

# Refuse to update the wrong environment file.
DECLARED_ENV=$(awk -F= '
  /^[[:space:]]*(export[[:space:]]+)?DSE_DEPLOYMENT_ENVIRONMENT=/ {
    value=$0; sub(/^[^=]*=/,"",value); gsub(/^[[:space:]"\047]+|[[:space:]"\047]+$/,"",value); print toupper(value); exit
  }
' "$CURRENT")
[[ "$DECLARED_ENV" == "$EXPECTED_ENV" ]] || {
  echo "Environment safety mismatch: $ENV_FILE declares ${DECLARED_ENV:-<missing>}, expected $EXPECTED_ENV." >&2
  exit 2
}

# Preserve all existing environment content and replace only the update token.
awk '
  !/^[[:space:]]*(export[[:space:]]+)?DSE_GITHUB_UPDATE_TOKEN=/ { print }
' "$CURRENT" > "$UPDATED"
printf '\nDSE_GITHUB_UPDATE_TOKEN=%s\n' "$TOKEN" >> "$UPDATED"

OWNER=$(sudo -n stat -c '%u' "$ENV_FILE")
GROUP=$(sudo -n stat -c '%g' "$ENV_FILE")
MODE=$(sudo -n stat -c '%a' "$ENV_FILE")
[[ "$MODE" =~ ^[0-7]{3,4}$ ]] || { echo "Unable to preserve permissions for $ENV_FILE" >&2; exit 2; }

sudo -n install -o "$OWNER" -g "$GROUP" -m "$MODE" "$UPDATED" "$ENV_FILE"
if command -v restorecon >/dev/null 2>&1; then
  sudo -n restorecon "$ENV_FILE" >/dev/null 2>&1 || true
fi

# Verify presence without ever printing the credential.
sudo -n grep -q '^DSE_GITHUB_UPDATE_TOKEN=.' "$ENV_FILE" || {
  echo 'DSE_GITHUB_UPDATE_TOKEN was not persisted.' >&2
  exit 1
}

echo "GITHUB_UPDATE_TOKEN_CONFIGURED environment=$EXPECTED_ENV file=$ENV_FILE"
