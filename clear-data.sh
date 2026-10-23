#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ "${1:-}" != "--yes" ]]; then
  echo "Удаляет все прикладные данные и учётные записи, сохраняя схему БД."
  echo "Закройте JavaFX. Для подтверждения: ./clear-data.sh --yes"
  exit 1
fi
docker compose stop backend
docker compose up -d --wait postgres
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/clear-data.sql
echo "БД очищена. Таблицы, связи и справочник ролей сохранены."
echo "Запуск: docker compose up -d --build --wait (создаст начального администратора)."
