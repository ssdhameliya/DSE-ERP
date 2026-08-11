#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export DSE_POSTGRES_MODE=managed
if [[ -z "${DSE_POSTGRES_HOME:-}" && -z "${DSE_POSTGRES_RUNTIME_DIR:-}" ]]; then
  for candidate in "$ROOT/runtime/postgresql" "/opt/homebrew/opt/postgresql@18" "/usr/local/opt/postgresql@18" "/Library/PostgreSQL/18"; do
    if [[ -x "$candidate/bin/initdb" ]]; then export DSE_POSTGRES_RUNTIME_DIR="$candidate"; break; fi
  done
fi
if [[ -z "${DSE_POSTGRES_HOME:-}" && -z "${DSE_POSTGRES_RUNTIME_DIR:-}" ]]; then
  echo "PostgreSQL 18 runtime payload not found for this SOURCE/IntelliJ run." >&2
  echo "Customer DMG packaging bundles it automatically. For development set DSE_POSTGRES_RUNTIME_DIR." >&2
  exit 2
fi
mvn -pl server -am install -DskipTests
mvn -pl desktop javafx:run
