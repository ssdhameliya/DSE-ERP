#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
echo "Starting DSE ERP Spring Boot server on http://localhost:8080 ..."
mvn -f server/pom.xml spring-boot:run
