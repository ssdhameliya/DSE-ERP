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

# 5.1.32 managed PostgreSQL payload. For release packaging, point
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

# macOS PostgreSQL must be fully self-contained. Homebrew PostgreSQL binaries can
# contain absolute references into /opt/homebrew/Cellar or /usr/local/Cellar.
# Copy external dylibs into the app payload and rewrite every non-system Mach-O
# dependency to a path inside runtime/postgresql.
PG_BUNDLE="$INPUT/runtime/postgresql"
PG_DEPS="$PG_BUNDLE/lib/dse-deps"
mkdir -p "$PG_DEPS"

is_macho() {
  file "$1" 2>/dev/null | grep -q "Mach-O"
}

bundled_match_for_dependency() {
  local dep="$1"
  local base="${dep:t}"
  find "$PG_BUNDLE/lib" -type f -name "$base" ! -path "$PG_DEPS/*" -print -quit 2>/dev/null || true
}

relative_loader_reference() {
  local from="$1"
  local to="$2"
  python3 - "$from" "$to" <<'PY'
import os, sys
source=os.path.abspath(sys.argv[1])
target=os.path.abspath(sys.argv[2])
rel=os.path.relpath(target, os.path.dirname(source))
print("@loader_path/" + rel)
PY
}

copy_external_dylib() {
  local dep="$1"
  local source="$dep"
  if [[ ! -e "$source" ]]; then
    echo "ERROR: PostgreSQL dependency does not exist on build machine: $dep" >&2
    exit 1
  fi

  local real_source
  real_source="$(python3 - "$source" <<'PY'
import os, sys
print(os.path.realpath(sys.argv[1]))
PY
)"
  local base="${real_source:t}"
  local dest="$PG_DEPS/$base"

  if [[ -e "$dest" ]] && ! cmp -s "$real_source" "$dest"; then
    # Homebrew can legitimately provide different dylibs with the same basename
    # through separate dependency trees (for example libcom_err from krb5).
    # Give the second binary a deterministic unique filename and rewrite the
    # calling Mach-O file to that exact bundled copy.
    local digest stem ext
    digest="$(shasum -a 256 "$real_source" | awk '{print substr($1,1,10)}')"
    if [[ "$base" == *.dylib ]]; then
      stem="${base%.dylib}"
      ext=".dylib"
    else
      stem="$base"
      ext=""
    fi
    dest="$PG_DEPS/${stem}-dse-${digest}${ext}"
    echo "PostgreSQL dylib basename collision: $base -> ${dest:t}" >&2
  fi

  if [[ ! -e "$dest" ]]; then
    cp -L "$real_source" "$dest"
    chmod u+w "$dest"
  elif ! cmp -s "$real_source" "$dest"; then
    echo "ERROR: Deterministic PostgreSQL dylib collision could not be resolved." >&2
    echo "       Existing: $dest" >&2
    echo "       New:      $real_source" >&2
    exit 1
  fi

  print -r -- "$dest"
}

rewrite_macho_dependencies() {
  local file_path="$1"
  is_macho "$file_path" || return 0
  chmod u+w "$file_path" 2>/dev/null || true

  local deps
  deps="$(otool -L "$file_path" | tail -n +2 | awk '{print $1}')"
  local dep target replacement
  while IFS= read -r dep; do
    [[ -n "$dep" ]] || continue

    case "$dep" in
      /System/*|/usr/lib/*|@loader_path/*|@executable_path/*) continue ;;
    esac

    target=""

    # If the dependency is already part of the copied PostgreSQL lib tree,
    # prefer that exact bundled copy. This fixes libpq.5.dylib and similar
    # Homebrew absolute references without duplicating them.
    target="$(bundled_match_for_dependency "$dep")"

    if [[ -z "$target" ]]; then
      case "$dep" in
        @rpath/*)
          local rbase="${dep:t}"
          target="$(find "$PG_BUNDLE/lib" -type f -name "$rbase" -print -quit 2>/dev/null || true)"
          ;;
        /*)
          target="$(copy_external_dylib "$dep")"
          ;;
        *)
          echo "ERROR: Unsupported PostgreSQL dylib reference '$dep' in $file_path" >&2
          exit 1
          ;;
      esac
    fi

    [[ -n "$target" && -e "$target" ]] || {
      echo "ERROR: Could not bundle PostgreSQL dependency '$dep' required by $file_path" >&2
      exit 1
    }

    replacement="$(relative_loader_reference "$file_path" "$target")"
    install_name_tool -change "$dep" "$replacement" "$file_path"
  done <<< "$deps"

  # A copied dylib may carry an absolute install-id. Give it a portable id.
  if [[ "$file_path" == *.dylib ]]; then
    install_name_tool -id "@loader_path/${file_path:t}" "$file_path" 2>/dev/null || true
  fi
}

# Repeatedly scan because copied external dylibs can introduce more dependencies.
for pass in 1 2 3 4 5 6; do
  changed=0
  before_count="$(find "$PG_DEPS" -type f 2>/dev/null | wc -l | tr -d ' ')"
  while IFS= read -r candidate; do
    rewrite_macho_dependencies "$candidate"
  done < <(find "$PG_BUNDLE/bin" "$PG_BUNDLE/lib" -type f -print)
  after_count="$(find "$PG_DEPS" -type f 2>/dev/null | wc -l | tr -d ' ')"
  [[ "$after_count" == "$before_count" ]] || changed=1
  [[ "$changed" -eq 0 ]] && break
done

# Release gate: no packaged PostgreSQL Mach-O file may retain a Homebrew/Cellar
# or other non-system absolute dependency. This prevents creating another DMG
# that only works on the GitHub runner/build Mac.
bad_refs=0
while IFS= read -r candidate; do
  is_macho "$candidate" || continue
  while IFS= read -r dep; do
    [[ -n "$dep" ]] || continue
    case "$dep" in
      /System/*|/usr/lib/*|@loader_path/*|@executable_path/*) ;;
      *)
        echo "ERROR: External dylib reference remains:" >&2
        echo "       File: $candidate" >&2
        echo "       Ref : $dep" >&2
        bad_refs=1
        ;;
    esac
  done < <(otool -L "$candidate" | tail -n +2 | awk '{print $1}')
done < <(find "$PG_BUNDLE/bin" "$PG_BUNDLE/lib" -type f -print)
[[ "$bad_refs" -eq 0 ]] || exit 1

# Verify all copied dependency filenames are unique and readable after collision handling.
duplicate_names="$(find "$PG_DEPS" -type f -exec basename {} \; | sort | uniq -d)"
if [[ -n "$duplicate_names" ]]; then
  echo "ERROR: Duplicate dependency filenames remain after PostgreSQL collision handling:" >&2
  echo "$duplicate_names" >&2
  exit 1
fi
echo "Verified PostgreSQL dylib basename collisions are safely isolated."

# Explicitly verify the commands required for workspace creation.
for pg_cmd in postgres pg_ctl initdb createdb psql; do
  pg_path="$PG_BUNDLE/bin/$pg_cmd"
  [[ -x "$pg_path" ]] || { echo "ERROR: Bundled PostgreSQL command missing: $pg_path" >&2; exit 1; }
  echo "Verified self-contained PostgreSQL command: $pg_cmd"
  otool -L "$pg_path"
done

cp "$ROOT/runtime/runtime-manifest.properties" "$INPUT/runtime/runtime-manifest.properties"
echo "Bundled self-contained PostgreSQL runtime: $POSTGRES_RUNTIME"
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
[[ -x "$APP_PG/bin/postgres" && -x "$APP_PG/bin/initdb" ]] || {
  echo "ERROR: Packaged app image is missing PostgreSQL runtime commands." >&2
  exit 1
}
if otool -L "$APP_PG/bin/postgres" | grep -E '/opt/homebrew|/usr/local/(Cellar|opt)|/Library/PostgreSQL' >/dev/null; then
  echo "ERROR: Packaged postgres still depends on an external PostgreSQL/Homebrew path." >&2
  otool -L "$APP_PG/bin/postgres" >&2
  exit 1
fi
echo "Verified packaged PostgreSQL is independent of Homebrew paths."

jpackage --type dmg "${COMMON[@]}" --dest "$DEST"

DMG="$(find "$DEST" -maxdepth 1 -name '*.dmg' -print -quit)"
[[ -n "$DMG" ]] || { echo "macOS DMG was not produced." >&2; exit 1; }
FINAL="DSE-ERP-$VERSION-macOS-$ARCH_LABEL.dmg"
mv "$DMG" "$DEST/$FINAL"
shasum -a 256 "$DEST/$FINAL" | sed "s#  .*/#  #" > "$DEST/checksums-macos-$ARCH_LABEL.txt"
echo "Created: $DEST/$FINAL"
