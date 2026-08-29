#!/usr/bin/env sh
set -eu
MAVEN_VERSION="3.9.11"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_USER_HOME=${MAVEN_USER_HOME:-"$HOME/.m2"}
DIST_DIR="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_HOME="$DIST_DIR/apache-maven-$MAVEN_VERSION"
ARCHIVE="$DIST_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"
URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then wget -O "$ARCHIVE" "$URL"
    else echo "Maven Wrapper requires curl or wget for the first run." >&2; exit 1; fi
  fi
  rm -rf "$MAVEN_HOME"
  tar -xzf "$ARCHIVE" -C "$DIST_DIR"
fi
exec "$MAVEN_HOME/bin/mvn" -f "$BASE_DIR/pom.xml" "$@"
