#!/bin/zsh
set -euo pipefail

POSTGRES_VERSION="18.4"
POSTGRES_SHA256="81a81ec695fb0c7901407defaa1d2f7973617154cf27ba74e3a7ab8e64436094"
SOURCE_URL="https://ftp.postgresql.org/pub/source/v${POSTGRES_VERSION}/postgresql-${POSTGRES_VERSION}.tar.bz2"
DESTINATION="${1:-}"

[[ -n "$DESTINATION" ]] || {
  echo "Usage: $0 <absolute-runtime-destination>" >&2
  exit 1
}
[[ "$DESTINATION" == /* ]] || {
  echo "PostgreSQL runtime destination must be absolute: $DESTINATION" >&2
  exit 1
}

WORK="$(mktemp -d "${TMPDIR:-/tmp}/dse-postgresql-build.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
ARCHIVE="$WORK/postgresql-${POSTGRES_VERSION}.tar.bz2"
SOURCE="$WORK/postgresql-${POSTGRES_VERSION}"

echo "Downloading PostgreSQL ${POSTGRES_VERSION} source from the official PostgreSQL archive..."
curl --fail --location --retry 3 --silent --show-error "$SOURCE_URL" --output "$ARCHIVE"
echo "${POSTGRES_SHA256}  ${ARCHIVE}" | shasum -a 256 --check
tar -xjf "$ARCHIVE" -C "$WORK"

rm -rf "$DESTINATION"
mkdir -p "$DESTINATION"

echo "Building a minimal self-contained PostgreSQL ${POSTGRES_VERSION} runtime..."
cd "$SOURCE"
./configure \
  --prefix="$DESTINATION" \
  --disable-nls \
  --disable-rpath \
  --without-icu \
  --without-readline
make -j "$(sysctl -n hw.logicalcpu)"
make install

for command in initdb postgres pg_ctl psql createdb pg_config; do
  [[ -x "$DESTINATION/bin/$command" ]] || {
    echo "PostgreSQL source build is missing required command: $command" >&2
    exit 1
  }
done
[[ -f "$DESTINATION/share/postgres.bki" ]] || {
  echo "PostgreSQL source build is missing share/postgres.bki" >&2
  exit 1
}

expected="$(cd "$DESTINATION" && pwd -P)"
for specification in "--bindir:$expected/bin" "--sharedir:$expected/share" "--pkglibdir:$expected/lib"; do
  flag="${specification%%:*}"
  wanted="${specification#*:}"
  actual="$($DESTINATION/bin/pg_config "$flag")"
  [[ "$actual" == "$wanted" ]] || {
    echo "PostgreSQL runtime is not relocatable: pg_config $flag returned $actual, expected $wanted" >&2
    exit 1
  }
done

echo "Created relocatable PostgreSQL runtime: $DESTINATION"
