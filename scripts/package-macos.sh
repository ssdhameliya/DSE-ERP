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

JAR="$ROOT/target/DSE_Final.jar"
[[ -f "$JAR" ]] || { echo "Packaged JAR not found: $JAR" >&2; exit 1; }
INPUT="$ROOT/target/jpackage-input"
DEST="$ROOT/target/macos-installer"
APP_IMAGE="$ROOT/target/macos-app-image"
rm -rf "$INPUT" "$DEST" "$APP_IMAGE"
mkdir -p "$INPUT" "$DEST" "$APP_IMAGE"
cp "$JAR" "$INPUT/DSE_Final.jar"

PNG="$ROOT/src/main/resources/installer/logo-1024.png"
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
  --description "Open-source JavaFX ERP desktop application"
  --copyright "Copyright (c) DS Engineers"
  --input "$INPUT"
  --main-jar "DSE_Final.jar"
  --main-class "org.example.app.Launcher"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-Ddse.erp.nativeAccessRelaunch=true"
  --mac-package-identifier "com.dsengineers.dseerp"
  --mac-package-name "DSE ERP"
)
[[ -f "$ICNS" ]] && COMMON+=(--icon "$ICNS")

jpackage --type app-image "${COMMON[@]}" --dest "$APP_IMAGE"
jpackage --type dmg "${COMMON[@]}" --dest "$DEST"

DMG="$(find "$DEST" -maxdepth 1 -name '*.dmg' -print -quit)"
[[ -n "$DMG" ]] || { echo "macOS DMG was not produced." >&2; exit 1; }
FINAL="DSE-ERP-$VERSION-macOS-$ARCH_LABEL.dmg"
mv "$DMG" "$DEST/$FINAL"
shasum -a 256 "$DEST/$FINAL" | sed "s#  .*/#  #" > "$DEST/checksums-macos-$ARCH_LABEL.txt"
echo "Created: $DEST/$FINAL"
