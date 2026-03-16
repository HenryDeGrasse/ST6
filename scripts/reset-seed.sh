#!/bin/bash
# ---------------------------------------------------------------------------
# Reset and re-seed the local development database.
# Clears all plan/commit/actual/review/notification data then re-applies seed.
#
# Usage:
#   ./scripts/reset-seed.sh              # uses local psql
#   ./scripts/reset-seed.sh --docker     # uses Docker exec
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-weekly}"
DB_USER="${POSTGRES_USER:-weekly}"

RESET_SQL="
DELETE FROM weekly_commit_actuals;
DELETE FROM manager_reviews;
DELETE FROM notifications;
DELETE FROM weekly_commits;
DELETE FROM weekly_plans;
DELETE FROM idempotency_keys;
"

if [[ "${1:-}" == "--docker" ]]; then
  echo "▸ Resetting data via Docker..."
  echo "$RESET_SQL" | docker compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME"
  echo "▸ Re-seeding..."
  docker compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" < "$ROOT_DIR/scripts/seed-data.sql"
else
  echo "▸ Resetting data via local psql..."
  PGPASSWORD="${POSTGRES_PASSWORD:-weekly}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$RESET_SQL"
  echo "▸ Re-seeding..."
  PGPASSWORD="${POSTGRES_PASSWORD:-weekly}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/scripts/seed-data.sql"
fi

echo "✔ Database reset and re-seeded successfully."
