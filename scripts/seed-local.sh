#!/bin/bash
# ---------------------------------------------------------------------------
# Seed script for local development (PRD §12.9)
# Creates test users, RCDO tree, and sample plans in various states.
# ---------------------------------------------------------------------------
set -euo pipefail

# Source .env if available (same vars as docker-compose + dev.sh)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "$SCRIPT_DIR/../.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/../.env"
  set +a
fi

DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-weekly}"
DB_USER="${POSTGRES_USER:-weekly}"
DB_PASS="${POSTGRES_PASSWORD:-weekly}"

PSQL="psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER"

echo "🌱 Seeding local development data..."

export PGPASSWORD="$DB_PASS"

$PSQL < "$SCRIPT_DIR/seed-data.sql"

echo "✅ Local seed data created successfully."
echo ""
echo "Test org:     a0000000-0000-0000-0000-000000000001"
echo "Test user:    c0000000-0000-0000-0000-000000000001"
echo "Test plan:    b0000000-0000-0000-0000-000000000001 (DRAFT)"
