#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
container="ticketpoint-kr3-test-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run --rm -d --name "$container"   -e POSTGRES_DB=ticketpoint_test -e POSTGRES_USER=ticketpoint_test   -e POSTGRES_PASSWORD=isolated-test-password   -p 127.0.0.1::5432 postgres:17-alpine >/dev/null
ready=false
for _ in {1..45}; do
  if docker exec "$container" pg_isready -U ticketpoint_test -d ticketpoint_test >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then echo "Test PostgreSQL did not become ready" >&2; exit 1; fi
port="$(docker port "$container" 5432/tcp | awk -F: '{print $NF}')"
export KR3_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$port/ticketpoint_test"
export KR3_TEST_DB_USER=ticketpoint_test
export KR3_TEST_DB_PASSWORD=isolated-test-password
export TEST_DB_URL="$KR3_TEST_DB_URL"
export TEST_DB_USER="$KR3_TEST_DB_USER"
export TEST_DB_PASSWORD="$KR3_TEST_DB_PASSWORD"
./mvnw clean verify
