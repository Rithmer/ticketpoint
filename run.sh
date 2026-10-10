#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  echo 'Создайте .env из .env.example и задайте POSTGRES_PASSWORD.' >&2
  exit 1
fi
set -a
source .env
set +a
exec ./mvnw javafx:run "$@"
