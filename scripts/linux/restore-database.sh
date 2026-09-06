#!/usr/bin/env bash
set -euo pipefail
BACKUP=${1:?validated .pgbackup}
DB=${2:?database name}
USER=${3:?database user}
[[ -s "$BACKUP" ]] || { echo 'Backup is missing/empty' >&2; exit 1; }
pg_restore --list "$BACKUP" >/dev/null
pg_restore --clean --if-exists --no-owner --no-privileges --username="$USER" --dbname="$DB" "$BACKUP"
echo "Restore completed into $DB"
