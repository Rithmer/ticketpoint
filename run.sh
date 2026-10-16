#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec ./mvnw -pl javafx-client -am javafx:run "$@"
