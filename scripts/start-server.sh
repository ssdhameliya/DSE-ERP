#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
echo "Building DSE ERP backend..."
mvn -pl server -am package -DskipTests
echo "Starting packaged DSE ERP backend on http://localhost:8080 ..."
exec java -jar server/target/dse-erp-server.jar
