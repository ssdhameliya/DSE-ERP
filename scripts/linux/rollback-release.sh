#!/usr/bin/env bash
set -euo pipefail
ENVIRONMENT=${1:?usage: rollback-release.sh <uat|prod> <release-directory-or-version>}
TARGET=${2:?previous release version or absolute release directory}
case "$ENVIRONMENT" in uat|prod) ;; *) echo 'environment must be uat or prod' >&2; exit 2;; esac
BASE="/opt/dse-erp/$ENVIRONMENT"
SERVICE="dse-erp@$ENVIRONMENT"
if [[ "$TARGET" = /* ]]; then RELEASE="$TARGET"; else RELEASE="$BASE/releases/$TARGET"; fi
[[ -s "$RELEASE/dse-erp-server.jar" ]] || { echo "Release not found: $RELEASE" >&2; exit 2; }
sudo systemctl stop "$SERVICE" 2>/dev/null || true
sudo ln -sfn "$RELEASE" "$BASE/current"
sudo systemctl start "$SERVICE"
echo "Server binary rolled back to $RELEASE."
echo 'If a database schema migration must also be reversed, restore the matching validated pre-upgrade backup before user access.'
