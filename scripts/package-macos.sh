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

# 6.0.3 managed PostgreSQL payload. For release packaging, point
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
  # -L dereferences formula symlinks so the packaged runtime never points back to Homebrew.
  cp -RL "$POSTGRES_RUNTIME/$folder" "$INPUT/runtime/postgresql/$folder"
done

# Homebrew's PostgreSQL formula keeps initdb bootstrap files in pkgshare
# (typically /opt/homebrew/share/postgresql@18), which is outside the formula's
# opt prefix on some runners.  A copied bin/lib runtime can therefore execute
# but initdb later fails on a clean Mac looking for the build-machine postgres.bki.
# Ensure postgres.bki and its companion bootstrap files are physically bundled.
if ! find "$INPUT/runtime/postgresql/share" -name postgres.bki -type f -print -quit | grep -q .; then
  HOMEBREW_PREFIX="$(brew --prefix 2>/dev/null || true)"
  PG_PKGSHARE=""
  for candidate in \
      "$POSTGRES_RUNTIME/share/postgresql@18" \
      "$POSTGRES_RUNTIME/share" \
      "${HOMEBREW_PREFIX:+$HOMEBREW_PREFIX/share/postgresql@18}"; do
    if [[ -n "$candidate" && -f "$candidate/postgres.bki" ]]; then
      PG_PKGSHARE="$candidate"
      break
    fi
  done
  [[ -n "$PG_PKGSHARE" ]] || {
    echo "PostgreSQL bootstrap files not found (postgres.bki). Refusing to build an incomplete macOS runtime." >&2
    exit 1
  }
  mkdir -p "$INPUT/runtime/postgresql/share/postgresql@18"
  cp -RL "$PG_PKGSHARE/." "$INPUT/runtime/postgresql/share/postgresql@18/"
fi

BUNDLED_PG_BKI="$(find "$INPUT/runtime/postgresql/share" -name postgres.bki -type f -print -quit)"
[[ -n "$BUNDLED_PG_BKI" ]] || { echo "Bundled PostgreSQL postgres.bki is missing after staging." >&2; exit 1; }
echo "Bundled PostgreSQL bootstrap share: $(dirname "$BUNDLED_PG_BKI")"

cp "$ROOT/runtime/runtime-manifest.properties" "$INPUT/runtime/runtime-manifest.properties"

# Homebrew PostgreSQL binaries are not relocatable by copying alone. Their Mach-O
# load commands contain build-runner paths such as /opt/homebrew/Cellar/.... Bundle
# transitive non-system dylibs and rewrite every load command before jpackage runs.
python3 "$ROOT/scripts/relocate-macos-postgresql.py" \
  "$INPUT/runtime/postgresql" "$POSTGRES_RUNTIME"

echo "Bundled relocatable PostgreSQL runtime: $POSTGRES_RUNTIME"
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

# Verify the runtime again from the actual .app layout. This catches any jpackage
# staging regression before a DMG can be uploaded to a release.
python3 "$ROOT/scripts/verify-production-bundle.py" "$APP_IMAGE/DSE ERP.app/Contents/app"

jpackage --type dmg "${COMMON[@]}" --dest "$DEST"

DMG="$(find "$DEST" -maxdepth 1 -name '*.dmg' -print -quit)"
[[ -n "$DMG" ]] || { echo "macOS DMG was not produced." >&2; exit 1; }
FINAL="DSE-ERP-$VERSION-macOS-$ARCH_LABEL.dmg"
mv "$DMG" "$DEST/$FINAL"
shasum -a 256 "$DEST/$FINAL" | sed "s#  .*/#  #" > "$DEST/checksums-macos-$ARCH_LABEL.txt"
echo "Created: $DEST/$FINAL"
