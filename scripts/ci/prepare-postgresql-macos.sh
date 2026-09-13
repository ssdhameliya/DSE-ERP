#!/bin/zsh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if (( $# >= 1 )); then
  RUNTIME_DIR="$1"
else
  [[ -n "${RUNNER_TEMP:-}" ]] || { echo "RUNNER_TEMP is not set and no runtime directory was supplied" >&2; exit 1; }
  RUNTIME_DIR="$RUNNER_TEMP/dse-postgresql-18.4"
fi

if [[ ! -x "$RUNTIME_DIR/bin/initdb" ]]; then
  echo "PostgreSQL 18.4 relocatable runtime cache miss; building it once so the default branch can seed tag releases."
  zsh "$ROOT/scripts/build-postgresql-macos.sh" "$RUNTIME_DIR"
else
  echo "Using cached PostgreSQL 18.4 runtime: $RUNTIME_DIR"
fi

for binary in initdb pg_ctl pg_isready psql createdb pg_dump pg_restore pg_config; do
  [[ -x "$RUNTIME_DIR/bin/$binary" ]] || { echo "PostgreSQL runtime is incomplete. Missing: $RUNTIME_DIR/bin/$binary" >&2; exit 1; }
done
for folder in lib share; do
  [[ -d "$RUNTIME_DIR/$folder" ]] || { echo "PostgreSQL runtime is incomplete. Missing folder: $RUNTIME_DIR/$folder" >&2; exit 1; }
done

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "DSE_POSTGRES_RUNTIME_DIR=$RUNTIME_DIR" >> "$GITHUB_ENV"
fi
echo "POSTGRES_RUNTIME_READY platform=macos arch=$(uname -m) path=$RUNTIME_DIR"
