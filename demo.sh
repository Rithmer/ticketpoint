#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "Не найден .env. Сначала скопируйте .env.example в .env и заполните пароли и JWT_SECRET_BASE64." >&2
  exit 1
fi

docker compose up -d --build --wait
docker compose run --rm --no-deps backend --ticketpoint.demo-seed.enabled=true

cat <<'EOF'
Демо-данные готовы. Сайт: http://localhost:8080
demo-admin / DemoAdmin123!
demo-manager / DemoManager123!
demo-viewer / DemoViewer123!
demo-buyer / DemoBuyer123!
EOF
