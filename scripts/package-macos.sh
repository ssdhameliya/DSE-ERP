#!/bin/zsh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
VERSION="${1:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | tail -1)}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Invalid application version: $VERSION" >&2
  exit 1
fi

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) ARCH_LABEL="arm64" ;;
  x86_64) ARCH_LABEL="x86_64" ;;
  *) echo "Unsupported macOS architecture: $ARCH" >&2; exit 1 ;;
esac

echo "Building DSE ERP $VERSION for macOS $ARCH_LABEL..."
mvn -B -ntp clean verify

JAR="$ROOT/desktop/target/DSE_Final.jar"
SERVER_JAR="$ROOT/server/target/dse-erp-server.jar"
[[ -f "$JAR" ]] || { echo "Packaged desktop JAR not found: $JAR" >&2; exit 1; }
[[ -f "$SERVER_JAR" ]] || { echo "Packaged server JAR not found: $SERVER_JAR" >&2; exit 1; }
INPUT="$ROOT/target/jpackage-input"
DEST="$ROOT/target/macos-installer"
APP_IMAGE="$ROOT/target/macos-app-image"
rm -rf "$INPUT" "$DEST" "$APP_IMAGE"
mkdir -p "$INPUT" "$DEST" "$APP_IMAGE"
cp "$JAR" "$INPUT/DSE_Final.jar"
mkdir -p "$INPUT/server"
cp "$SERVER_JAR" "$INPUT/server/dse-erp-server.jar"

# 5.1.39 managed PostgreSQL payload. For release packaging, point
# DSE_POSTGRES_RUNTIME_DIR at a verified PostgreSQL 18 binary distribution for this architecture.
POSTGRES_RUNTIME="${DSE_POSTGRES_RUNTIME_DIR:-}"
if [[ -z "$POSTGRES_RUNTIME" ]]; then
  for candidate in "/opt/homebrew/opt/postgresql@18" "/usr/local/opt/postgresql@18" "/Library/PostgreSQL/18"; do
    if [[ -x "$candidate/bin/initdb" ]]; then POSTGRES_RUNTIME="$candidate"; break; fi
  done
fi
[[ -n "$POSTGRES_RUNTIME" && -x "$POSTGRES_RUNTIME/bin/initdb" ]] || {
  echo "PostgreSQL 18 runtime not found. Set DSE_POSTGRES_RUNTIME_DIR to a verified PostgreSQL 18 binary distribution." >&2
  exit 1
}
mkdir -p "$INPUT/runtime/postgresql"
for folder in bin lib share; do
  [[ -d "$POSTGRES_RUNTIME/$folder" ]] || { echo "PostgreSQL runtime folder missing: $POSTGRES_RUNTIME/$folder" >&2; exit 1; }
  cp -R "$POSTGRES_RUNTIME/$folder" "$INPUT/runtime/postgresql/$folder"
done

# Keep the proven 5.1.39 PostgreSQL layout exactly as copied above. The only
# macOS portability work below is to relocate dynamic-library dependencies that
# still point outside the bundle. External libraries are placed in a bucket
# derived from their ORIGINAL source directory, while retaining their ORIGINAL
# filename. This prevents basename collisions without breaking ICU/Kerberos
# @loader_path sibling references.
PG_BUNDLE="$INPUT/runtime/postgresql"
PG_DEPS="$PG_BUNDLE/lib/dse-deps"
PG_MAP="$PG_DEPS/.dse-source-map.tsv"
mkdir -p "$PG_DEPS"
: > "$PG_MAP"

is_macho() {
  file "$1" 2>/dev/null | grep -q "Mach-O"
}

original_source_for() {
  local bundled="$1"
  awk -F '\t' -v dst="$bundled" '$2==dst {print $1; exit}' "$PG_MAP"
}

record_source_map() {
  local src="$1" dst="$2"
  if ! awk -F '\t' -v src="$src" '$1==src {found=1} END{exit !found}' "$PG_MAP"; then
    printf '%s\t%s\n' "$src" "$dst" >> "$PG_MAP"
  fi
}

bundle_external_dylib() {
  local source="$1"
  [[ -e "$source" ]] || {
    echo "ERROR: External PostgreSQL dylib is missing on build runner: $source" >&2
    exit 1
  }

  local real_source source_dir dir_hash dest_dir dest
  real_source="$(python3 - "$source" <<'PY'
import os, sys
print(os.path.realpath(sys.argv[1]))
PY
)"
  source_dir="${real_source:h}"
  dir_hash="$(printf '%s' "$source_dir" | shasum -a 256 | awk '{print substr($1,1,12)}')"
  dest_dir="$PG_DEPS/$dir_hash"
  dest="$dest_dir/${real_source:t}"

  mkdir -p "$dest_dir"
  if [[ ! -e "$dest" ]]; then
    cp -L "$real_source" "$dest"
    chmod u+w "$dest"
    echo "Bundled PostgreSQL dependency: $real_source -> $dest" >&2
  fi
  record_source_map "$real_source" "$dest"
  print -r -- "$dest"
}

relative_loader_reference() {
  python3 - "$1" "$2" <<'PY'
import os, sys
source=os.path.abspath(sys.argv[1])
target=os.path.abspath(sys.argv[2])
print("@loader_path/" + os.path.relpath(target, os.path.dirname(source)))
PY
}

find_bundled_postgres_lib() {
  local basename="$1"
  find "$PG_BUNDLE/lib" -type f -name "$basename" ! -path "$PG_DEPS/*" -print -quit 2>/dev/null || true
}

rewrite_macho_dependencies() {
  local candidate="$1"
  is_macho "$candidate" || return 0
  chmod u+w "$candidate" 2>/dev/null || true

  local original_source original_dir dep target replacement sibling
  original_source="$(original_source_for "$candidate")"
  original_dir=""
  [[ -n "$original_source" ]] && original_dir="${original_source:h}"

  while IFS= read -r dep; do
    [[ -n "$dep" ]] || continue
    case "$dep" in
      /System/*|/usr/lib/*|@executable_path/*)
        continue
        ;;
      @loader_path/*)
        # Leave valid local sibling references alone.
        sibling="${candidate:h}/${dep#@loader_path/}"
        [[ -e "$sibling" ]] && continue

        # For copied external libraries, resolve the sibling from the same
        # ORIGINAL source directory and put it in the same destination bucket.
        if [[ -n "$original_dir" && -e "$original_dir/${dep#@loader_path/}" ]]; then
          target="$(bundle_external_dylib "$original_dir/${dep#@loader_path/}")"
          replacement="$(relative_loader_reference "$candidate" "$target")"
          install_name_tool -change "$dep" "$replacement" "$candidate"
          continue
        fi

        # PostgreSQL's own lib directory is the final local fallback.
        target="$(find_bundled_postgres_lib "${dep#@loader_path/}")"
        if [[ -n "$target" ]]; then
          replacement="$(relative_loader_reference "$candidate" "$target")"
          install_name_tool -change "$dep" "$replacement" "$candidate"
          continue
        fi

        echo "ERROR: Unresolved @loader_path dependency '$dep' in $candidate" >&2
        exit 1
        ;;
      @rpath/*)
        local rbase="${dep:t}"
        target="$(find_bundled_postgres_lib "$rbase")"
        if [[ -z "$target" && -n "$original_dir" && -e "$original_dir/$rbase" ]]; then
          target="$(bundle_external_dylib "$original_dir/$rbase")"
        fi
        [[ -n "$target" ]] || {
          echo "ERROR: Unresolved @rpath dependency '$dep' in $candidate" >&2
          exit 1
        }
        replacement="$(relative_loader_reference "$candidate" "$target")"
        install_name_tool -change "$dep" "$replacement" "$candidate"
        ;;
      /*)
        # Prefer the PostgreSQL lib already copied by 5.1.39 when names match.
        target="$(find_bundled_postgres_lib "${dep:t}")"
        [[ -n "$target" ]] || target="$(bundle_external_dylib "$dep")"
        replacement="$(relative_loader_reference "$candidate" "$target")"
        install_name_tool -change "$dep" "$replacement" "$candidate"
        ;;
      *)
        echo "ERROR: Unsupported dylib reference '$dep' in $candidate" >&2
        exit 1
        ;;
    esac
  done < <(otool -L "$candidate" | tail -n +2 | awk '{print $1}')
}

# Iterate because copying one external dylib may introduce another.
for pass in 1 2 3 4 5 6 7 8; do
  before="$(find "$PG_DEPS" -type f ! -name '.dse-source-map.tsv' | wc -l | tr -d ' ')"
  while IFS= read -r candidate; do
    rewrite_macho_dependencies "$candidate"
  done < <(find "$PG_BUNDLE/bin" "$PG_BUNDLE/lib" -type f ! -name '.dse-source-map.tsv' -print)
  after="$(find "$PG_DEPS" -type f ! -name '.dse-source-map.tsv' | wc -l | tr -d ' ')"
  [[ "$before" == "$after" ]] && break
done

# Release gate: no bundled Mach-O may point to Homebrew or another external
# absolute library. Valid references are system libraries or paths inside bundle.
bad_refs=0
while IFS= read -r candidate; do
  is_macho "$candidate" || continue
  while IFS= read -r dep; do
    [[ -n "$dep" ]] || continue
    case "$dep" in
      /System/*|/usr/lib/*|@loader_path/*|@executable_path/*) ;;
      *)
        echo "ERROR: External PostgreSQL dylib reference remains:" >&2
        echo "       file: $candidate" >&2
        echo "       ref : $dep" >&2
        bad_refs=1
        ;;
    esac
  done < <(otool -L "$candidate" | tail -n +2 | awk '{print $1}')
done < <(find "$PG_BUNDLE/bin" "$PG_BUNDLE/lib" -type f ! -name '.dse-source-map.tsv' -print)
[[ "$bad_refs" -eq 0 ]] || exit 1

# Locate initdb support files INSIDE the untouched 5.1.39 share tree. Homebrew
# sometimes nests them under share/postgresql@18; do not restructure the tree.
PG_INIT_SHARE="$(python3 - "$PG_BUNDLE/share" <<'PY'
import os, sys
root=os.path.abspath(sys.argv[1])
for current, dirs, files in os.walk(root):
    depth=os.path.relpath(current, root).count(os.sep)
    if "postgres.bki" in files:
        print(current)
        raise SystemExit(0)
    if depth >= 3:
        dirs[:] = []
raise SystemExit(1)
PY
)" || {
  echo "ERROR: postgres.bki not found anywhere inside bundled PostgreSQL share tree." >&2
  find "$PG_BUNDLE/share" -maxdepth 4 -print >&2 || true
  exit 1
}
echo "Bundled initdb share directory: $PG_INIT_SHARE"

# Critical release smoke test: actually initialize a temporary PostgreSQL
# cluster using only the relocated runtime and bundled share directory.
PG_SMOKE="$ROOT/target/postgres-smoke-data"
rm -rf "$PG_SMOKE"
env DYLD_LIBRARY_PATH="" DYLD_FALLBACK_LIBRARY_PATH="" \
  "$PG_BUNDLE/bin/initdb" \
  -L "$PG_INIT_SHARE" \
  -D "$PG_SMOKE" \
  --no-sync \
  --encoding=UTF8 \
  --locale=C \
  --auth-local=trust \
  --auth-host=trust >/tmp/dse-initdb-smoke.log 2>&1 || {
    echo "ERROR: Bundled PostgreSQL initdb smoke test failed." >&2
    cat /tmp/dse-initdb-smoke.log >&2 || true
    exit 1
  }
echo "Verified bundled PostgreSQL initdb smoke test."

PG_SMOKE_PORT="$(python3 - <<'PY'
import socket
with socket.socket() as s:
    s.bind(("127.0.0.1", 0))
    print(s.getsockname()[1])
PY
)"

env DYLD_LIBRARY_PATH="" DYLD_FALLBACK_LIBRARY_PATH="" \
  "$PG_BUNDLE/bin/pg_ctl" \
  -D "$PG_SMOKE" \
  -o "-h 127.0.0.1 -p $PG_SMOKE_PORT" \
  -w start >/tmp/dse-postgres-start-smoke.log 2>&1 || {
    echo "ERROR: Bundled PostgreSQL server smoke test failed to start." >&2
    cat /tmp/dse-postgres-start-smoke.log >&2 || true
    rm -rf "$PG_SMOKE"
    exit 1
  }

env DYLD_LIBRARY_PATH="" DYLD_FALLBACK_LIBRARY_PATH="" \
  "$PG_BUNDLE/bin/pg_ctl" \
  -D "$PG_SMOKE" \
  -m fast \
  -w stop >/tmp/dse-postgres-stop-smoke.log 2>&1 || {
    echo "ERROR: Bundled PostgreSQL server smoke test failed to stop cleanly." >&2
    cat /tmp/dse-postgres-stop-smoke.log >&2 || true
    exit 1
  }

rm -rf "$PG_SMOKE"
echo "Verified bundled PostgreSQL server start/stop smoke test."

cp "$ROOT/runtime/runtime-manifest.properties" "$INPUT/runtime/runtime-manifest.properties"
echo "Bundled PostgreSQL runtime: $POSTGRES_RUNTIME"
python3 "$ROOT/scripts/verify-production-bundle.py" "$INPUT"

PNG="$ROOT/desktop/src/main/resources/installer/logo-1024.png"
ICNS="$ROOT/target/DSE-ERP.icns"
if [[ -f "$PNG" ]]; then
  ICONSET="$ROOT/target/DSE-ERP.iconset"
  rm -rf "$ICONSET" && mkdir -p "$ICONSET"
  for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$PNG" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
    double=$((size * 2))
    sips -z "$double" "$double" "$PNG" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
  done
  iconutil -c icns "$ICONSET" -o "$ICNS"
fi

COMMON=(
  --name "DSE ERP"
  --app-version "$VERSION"
  --vendor "DS Engineers"
  --description "DSE ERP desktop business management application"
  --copyright "Copyright (c) DS Engineers"
  --input "$INPUT"
  --main-jar "DSE_Final.jar"
  --main-class "org.example.app.Launcher"
  --jlink-options "--strip-debug --no-man-pages --no-header-files"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-Ddse.erp.nativeAccessRelaunch=true"
  --java-options "-Ddse.erp.packaged=true"
  --mac-package-identifier "com.dsengineers.dseerp"
  --mac-package-name "DSE ERP"
)
[[ -f "$ICNS" ]] && COMMON+=(--icon "$ICNS")

jpackage --type app-image "${COMMON[@]}" --dest "$APP_IMAGE"

BUNDLED_JAVA="$APP_IMAGE/DSE ERP.app/Contents/runtime/Contents/Home/bin/java"
if [[ ! -x "$BUNDLED_JAVA" ]]; then
  echo "ERROR: Production app image is missing bundled Java launcher: $BUNDLED_JAVA" >&2
  exit 1
fi
echo "Verified bundled Java launcher: $BUNDLED_JAVA"

APP_PG="$APP_IMAGE/DSE ERP.app/Contents/app/runtime/postgresql"
[[ -x "$APP_PG/bin/initdb" && -x "$APP_PG/bin/postgres" ]] || {
  echo "ERROR: Packaged app image is missing PostgreSQL commands." >&2
  exit 1
}
if ! find "$APP_PG/share" -maxdepth 4 -type f -name postgres.bki -print -quit | grep -q .; then
  echo "ERROR: Packaged app image does not contain postgres.bki." >&2
  exit 1
fi
if find "$APP_PG/bin" "$APP_PG/lib" -type f -print0 | xargs -0 file 2>/dev/null | grep 'Mach-O' | cut -d: -f1 | while IFS= read -r f; do otool -L "$f"; done | grep -E '/opt/homebrew|/usr/local/(Cellar|opt)|/Library/PostgreSQL' >/dev/null; then
  echo "ERROR: Packaged PostgreSQL still contains an external Homebrew/PostgreSQL dylib reference." >&2
  exit 1
fi
echo "Verified packaged PostgreSQL runtime is self-contained."

jpackage --type dmg "${COMMON[@]}" --dest "$DEST"

DMG="$(find "$DEST" -maxdepth 1 -name '*.dmg' -print -quit)"
[[ -n "$DMG" ]] || { echo "macOS DMG was not produced." >&2; exit 1; }
FINAL="DSE-ERP-$VERSION-macOS-$ARCH_LABEL.dmg"
mv "$DMG" "$DEST/$FINAL"
shasum -a 256 "$DEST/$FINAL" | sed "s#  .*/#  #" > "$DEST/checksums-macos-$ARCH_LABEL.txt"
echo "Created: $DEST/$FINAL"
