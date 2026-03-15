#!/bin/bash
# ---------------------------------------------------------------------------
# Start the full Weekly Commitments stack for local development.
#
# Usage:
#   ./scripts/dev.sh          # start everything
#   ./scripts/dev.sh --seed   # start everything + seed sample data
#   ./scripts/dev.sh --stop   # tear down everything
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# ── Load .env ────────────────────────────────────────────────────────────────
if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT_DIR/.env"
  set +a
else
  echo "⚠  No .env file found. Copy the example and fill in your values:"
  echo "   cp .env.example .env"
  echo ""
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

PIDFILE="$ROOT_DIR/.dev-pids"

# ── Helpers ──────────────────────────────────────────────────────────────────

log()  { echo -e "${CYAN}▸${NC} $*"; }
ok()   { echo -e "${GREEN}✔${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }
err()  { echo -e "${RED}✖${NC} $*" >&2; }

cleanup() {
  log "Shutting down dev processes..."
  if [[ -f "$PIDFILE" ]]; then
    while IFS= read -r pid; do
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
      fi
    done < "$PIDFILE"
    rm -f "$PIDFILE"
  fi
  # Also kill any child background jobs from this script
  jobs -p 2>/dev/null | xargs -r kill 2>/dev/null || true
  ok "All dev processes stopped."
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local max_attempts="${3:-60}"
  local attempt=0

  while ! curl -sf "$url" > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [[ $attempt -ge $max_attempts ]]; then
      err "$label did not become ready at $url after ${max_attempts}s"
      return 1
    fi
    sleep 1
  done
  ok "$label is ready ($url)"
}

save_pid() {
  echo "$1" >> "$PIDFILE"
}

# ── Stop mode ────────────────────────────────────────────────────────────────

if [[ "${1:-}" == "--stop" ]]; then
  cleanup
  log "Stopping Docker containers..."
  docker compose down
  ok "Everything stopped."
  exit 0
fi

SEED=false
if [[ "${1:-}" == "--seed" ]]; then
  SEED=true
fi

# ── Preflight checks ────────────────────────────────────────────────────────

log "Checking prerequisites..."

command -v docker >/dev/null 2>&1 || { err "Docker is required but not found."; exit 1; }
command -v node >/dev/null 2>&1   || { err "Node.js is required but not found."; exit 1; }
command -v java >/dev/null 2>&1   || { err "Java is required but not found."; exit 1; }

ok "Prerequisites OK (docker, node, java)"

# Clean up any leftover PIDs from a previous run
if [[ -f "$PIDFILE" ]]; then
  warn "Found stale PID file from a previous run, cleaning up..."
  cleanup
fi

# Register cleanup on exit
trap cleanup EXIT INT TERM

rm -f "$PIDFILE"

# ── Step 1: Docker infrastructure ────────────────────────────────────────────

log "Starting Docker infrastructure (Postgres, Redis, LocalStack)..."
docker compose up -d postgres redis localstack

log "Waiting for Postgres to be healthy..."
until docker compose exec -T postgres pg_isready -U weekly > /dev/null 2>&1; do
  sleep 1
done
ok "Postgres is healthy"

log "Waiting for Redis to be healthy..."
until docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; do
  sleep 1
done
ok "Redis is healthy"

# ── Step 1b: Stop Dockerized backend if running (we run it natively) ─────────

if docker compose ps --format '{{.Service}}' 2>/dev/null | grep -q weekly-service; then
  warn "Stopping Dockerized weekly-service (we'll run it natively instead)..."
  docker compose stop weekly-service
  docker compose rm -f weekly-service 2>/dev/null || true
fi

# Also kill anything else squatting on port 8080
if lsof -ti :8080 > /dev/null 2>&1; then
  warn "Port 8080 is in use — killing existing process..."
  lsof -ti :8080 | xargs kill -9 2>/dev/null || true
  sleep 1
fi

# ── Step 2: Backend ─────────────────────────────────────────────────────────

log "Starting backend (Spring Boot + Flyway migrations)..."
mkdir -p "$ROOT_DIR/backend/weekly-service/logs"

# Profile comes from .env (APP_ENV), defaults to local
PROFILE="${APP_ENV:-local}"
log "Spring profile: $PROFILE"

if [[ "${AI_PROVIDER:-stub}" == "openrouter" && -n "${OPENROUTER_API_KEY:-}" ]]; then
  log "AI provider: OpenRouter (model: ${OPENROUTER_MODEL:-anthropic/claude-sonnet-4})"
else
  log "AI provider: ${AI_PROVIDER:-stub} (set AI_PROVIDER=openrouter + OPENROUTER_API_KEY for real AI)"
fi

cd "$ROOT_DIR/backend/weekly-service"
./gradlew bootRun --args="--spring.profiles.active=$PROFILE" \
  > "$ROOT_DIR/backend/weekly-service/logs/bootRun.log" 2>&1 &
BACKEND_PID=$!
save_pid "$BACKEND_PID"
cd "$ROOT_DIR"

log "Waiting for backend to start (this may take 30-60s on first run)..."
wait_for_url "http://localhost:8080/actuator/health/liveness" "Backend (weekly-service)" 120

# ── Step 3: Seed data (optional) ────────────────────────────────────────────

if [[ "$SEED" == true ]]; then
  if command -v psql >/dev/null 2>&1; then
    log "Seeding local development data..."
    bash "$ROOT_DIR/scripts/seed-local.sh"
  else
    warn "psql not found — running seed via Docker instead..."
    docker compose exec -T postgres psql -U weekly -d weekly < "$ROOT_DIR/scripts/seed-data.sql"
    ok "Seed data applied via Docker"
  fi
fi

# ── Step 4: Frontend (pa-host-stub) ─────────────────────────────────────────

log "Starting frontend (PA host stub on http://localhost:3000)..."
npm run dev:host > "$ROOT_DIR/frontend-host.log" 2>&1 &
FRONTEND_PID=$!
save_pid "$FRONTEND_PID"

wait_for_url "http://localhost:3000" "Frontend (PA host stub)" 30

# ── Done ─────────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Weekly Commitments — Local Dev Stack Running${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${CYAN}Frontend (host stub):${NC}  http://localhost:3000"
echo -e "  ${CYAN}Frontend (standalone):${NC} (run 'npm run dev:frontend' for port 3001)"
echo -e "  ${CYAN}Backend API:${NC}           http://localhost:8080"
echo -e "  ${CYAN}Health check:${NC}          http://localhost:8080/actuator/health"
echo -e "  ${CYAN}Postgres:${NC}              localhost:5432  (weekly/weekly)"
echo -e "  ${CYAN}Redis:${NC}                 localhost:6379"
echo -e "  ${CYAN}LocalStack (SQS):${NC}      localhost:4566"
echo ""
if [[ "$SEED" == true ]]; then
echo -e "  ${CYAN}Test org:${NC}   a0000000-0000-0000-0000-000000000001"
echo -e "  ${CYAN}Test user:${NC}  c0000000-0000-0000-0000-000000000001"
echo ""
fi
echo -e "  ${YELLOW}Logs:${NC}"
echo -e "    Backend:  tail -f backend/weekly-service/logs/bootRun.log"
echo -e "    Frontend: tail -f frontend-host.log"
echo ""
echo -e "  ${YELLOW}Stop:${NC}  ./scripts/dev.sh --stop   (or Ctrl+C)"
echo ""

# Keep the script alive so trap fires on Ctrl+C
wait
