# Weekly Commitments

[![CI](https://github.com/your-org/weekly-commitments/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/weekly-commitments/actions/workflows/ci.yml)
![License: Proprietary](https://img.shields.io/badge/License-Proprietary-red.svg)

> **Weekly Commitments** is a production-focused module that connects individual
> weekly work to the strategic RCDO hierarchy (Rally Cries → Defining Objectives
> → Outcomes), replacing ad-hoc 15Five usage with structured, AI-assisted weekly
> planning and reconciliation.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
5. [Development Scripts](#development-scripts)
6. [CI Pipeline](#ci-pipeline)
7. [API Documentation](#api-documentation)
8. [Observability](#observability)
9. [Infrastructure](#infrastructure)
10. [Architecture Decision Records](#architecture-decision-records)
11. [Known Gaps / Roadmap](#known-gaps--roadmap)

---

## Overview

The Weekly Commitments (WC) module is a **micro-frontend embedded in the PA host
application** via the PM remote pattern, backed by a **Java 21 modular-monolith
service**. It provides:

- **Structured weekly planning** – ICs create commitments linked to Rally Cries,
  Defining Objectives, or Outcomes (or explicitly mark work as non-strategic).
- **Chess-layer prioritization** – Commitments are classified as KING / QUEEN /
  ROOK / BISHOP / KNIGHT / PAWN to express execution priority.
- **Weekly lifecycle state machine** – `DRAFT → LOCKED → RECONCILING →
  RECONCILED → CARRY_FORWARD` with a late-lock exception path.
- **Manager dashboard** – Real-time team roll-ups grouped by RCDO hierarchy, with
  risk and alignment signals.
- **AI-assisted RCDO suggestions** – LLM-powered suggestions for linking
  commitments to objectives, with graceful fallback to stubs.
- **Audit trail** – Append-only event log with a cryptographic hash chain for
  tamper detection (PRD §14.7).
- **Operational readiness** – Structured logging, Prometheus metrics, Grafana
  dashboards, and runbooks.

**PRD references:** §1–2 (background), §3 (users), §4 (scope), §14.7
(audit/security).

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  PA Host Application  (pa-host-stub for local dev)              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Weekly Commitments Micro-Frontend                      │    │
│  │  React 18 · TypeScript strict · Vite · Vitest           │    │
│  │  (PM remote pattern – loaded via Module Federation)     │    │
│  └──────────────────────┬──────────────────────────────────┘    │
└─────────────────────────│───────────────────────────────────────┘
                          │ REST / OpenAPI v1
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  weekly-service  (Spring Boot 3, Java 21)                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │
│  │  plan    │ │  audit   │ │   ai     │ │  notification    │   │
│  │  commit  │ │  hash    │ │ suggest  │ │  outbox / SQS    │   │
│  │  manager │ │  chain   │ │ recon    │ │  retention jobs  │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────────┬─────────┘   │
└───────┼────────────┼────────────┼─────────────────┼─────────────┘
        │            │            │                  │
   ┌────┴────┐  ┌────┴────┐  ┌───┴───┐         ┌───┴────┐
   │Postgres │  │  Redis  │  │  LLM  │         │  SQS   │
   │(Flyway) │  │(cache / │  │(OpenR │         │(Local  │
   │  RLS    │  │idempot.)│  │ outer)│         │ Stack) │
   └─────────┘  └─────────┘  └───────┘         └────────┘
```

**Key technology choices:**

| Layer | Technology |
|-------|-----------|
| Frontend language | TypeScript (strict), React 18 |
| Frontend build | Vite 6, Vitest 3 |
| Backend language | Java 21 |
| Backend framework | Spring Boot 3 |
| Database | PostgreSQL 16 (Flyway migrations, row-level security) |
| Cache / idempotency | Redis 7 |
| Async messaging | AWS SQS (LocalStack for local dev) |
| AI provider | OpenRouter (stub for local dev) |
| Infra-as-code | Terraform ≥ 1.6 |
| Container runtime | Docker / AWS ECS Fargate |
| Observability | Prometheus, Grafana, structured JSON logs |
| CI | GitHub Actions (7-gate pipeline) |

---

## Project Structure

```
weekly-commitments/
├── contracts/              # Shared OpenAPI spec + generated TypeScript client
│   ├── openapi.yaml        # Source of truth for the REST API contract
│   └── src/generated/      # Auto-generated TypeScript types (do not edit)
│
├── frontend/               # React micro-frontend (npm workspace)
│   └── src/
│       ├── pages/          # Plan editor, manager dashboard, reconciliation
│       ├── components/     # UI building blocks
│       ├── hooks/          # Data-fetching and business logic hooks
│       └── api/            # API client wrappers
│
├── pa-host-stub/           # Local PA host stub (npm workspace)
│                           # Simulates the embedding host for local dev
│
├── backend/
│   └── weekly-service/     # Spring Boot modular-monolith
│       ├── src/main/java/com/weekly/
│       │   ├── audit/      # Append-only audit log + hash chain + verification job
│       │   ├── plan/       # Weekly plan + commitment CRUD + lifecycle FSM
│       │   │               # + team dashboard (TeamDashboardController)
│       │   ├── ai/         # RCDO suggestion + reconciliation draft
│       │   ├── rcdo/       # RCDO read-through proxy (InMemoryRcdoClient + controller)
│       │   ├── notification/ # In-app notifications + retention job
│       │   ├── idempotency/  # Idempotency key filter + retention job
│       │   ├── outbox/     # Transactional outbox pattern for SQS events
│       │   ├── auth/       # JWT validation, org/role enforcement
│       │   ├── health/     # Custom Spring health indicators
│       │   ├── config/     # Spring configuration beans
│       │   └── shared/     # Shared utilities and domain types
│       └── src/main/resources/db/migration/  # Flyway SQL migrations
│
├── e2e/                    # Playwright end-to-end / acceptance tests
│   └── tests/              # Smoke + full suite (tagged SMOKE for Gate 7)
│
├── infra/
│   ├── terraform/          # IaC skeleton (main.tf, envs/)
│   ├── observability/      # Grafana dashboards + Prometheus alert rules
│   ├── localstack/         # LocalStack bootstrap (SQS queues)
│   └── flags/              # Feature flag inventory (feature-flags.json)
│
├── docs/
│   ├── prd.md              # Product Requirements Document
│   ├── deployment-readiness-checklist.md
│   ├── adrs/               # ADR index + drafted ADR-001…003 (ADR-004…016 scaffolded)
│   └── runbooks/           # Operational runbooks (R1–R10)
│
├── scripts/
│   ├── dev.sh              # Start / stop / seed local stack
│   ├── seed-local.sh       # Apply seed-data.sql to local Postgres
│   ├── seed-data.sql       # Sample org, users, plans, commitments
│   ├── openapi-diff.sh     # Breaking-change detector (wraps oasdiff for OpenAPI 3.1)
│   ├── semgrep-scan.sh     # Local SAST scan helper
│   └── rollback.sh         # Production rollback procedure
│
├── docker-compose.yml      # Local infrastructure (Postgres, Redis, LocalStack)
├── .env.example            # Environment variable template
└── package.json            # npm workspaces root (contracts, frontend, pa-host-stub)
```

---

## Getting Started

### Prerequisites

| Tool | Minimum version |
|------|----------------|
| Node.js | 20 |
| Java (JDK) | 21 |
| Docker + Docker Compose | 24 |
| (optional) psql | any |

### 1 — Clone and configure

```bash
git clone <repo-url> weekly-commitments
cd weekly-commitments
cp .env.example .env
# Edit .env if you want real AI (set AI_PROVIDER=openrouter + OPENROUTER_API_KEY)
```

### 2 — Install npm dependencies

```bash
npm ci
```

### 3 — Start the local stack

```bash
# Start Postgres + Redis + LocalStack + Spring Boot backend + PA host stub
./scripts/dev.sh --seed
```

This command:
1. Starts Docker infrastructure (Postgres, Redis, LocalStack SQS).
2. Runs Flyway migrations automatically when Spring Boot starts.
3. Seeds sample data (org `a0000000-…-000001`, user `c0000000-…-000001`).
4. Starts the frontend host stub at <http://localhost:3000>.

| Service | URL |
|---------|-----|
| PA host stub (frontend) | <http://localhost:3000> |
| Backend API | <http://localhost:8080> |
| Health check | <http://localhost:8080/actuator/health> |
| Prometheus metrics | <http://localhost:8080/actuator/prometheus> |
| Postgres | `localhost:5432` (weekly/weekly) |
| Redis | `localhost:6379` |
| LocalStack (SQS) | `localhost:4566` |

### 4 — Stop

```bash
./scripts/dev.sh --stop
# or Ctrl+C in the terminal running dev.sh
```

### AI provider (optional)

The backend ships with a **stub AI provider** that returns deterministic
responses — no API key needed. To use a real LLM:

```bash
# In .env:
AI_PROVIDER=openrouter
OPENROUTER_API_KEY=sk-or-…
OPENROUTER_MODEL=anthropic/claude-sonnet-4   # default
```

---

## Development Scripts

All scripts run from the project root.

### Frontend / contracts

| Command | Description |
|---------|-------------|
| `npm run typecheck` | TypeScript strict check across all workspaces |
| `npm run lint` | ESLint (frontend, contracts, pa-host-stub) + Checkstyle (backend) |
| `npm run test` | Unit tests for all workspaces + backend |
| `npm run build` | Production build for all workspaces + backend JAR |
| `npm run dev` | Start the full local stack with seed data |
| `npm run dev:frontend` | Start the React frontend in isolation (port 3001) |
| `npm run dev:host` | Start the PA host stub only (port 3000) |
| `npm run dev:stop` | Stop the local stack |

### Backend-specific

| Command | Description |
|---------|-------------|
| `npm run lint:backend` | Checkstyle (main + test) |
| `npm run unit:backend` | `./gradlew test` |
| `npm run build:backend` | `./gradlew build -x test` |
| `npm run migration:check` | Validate Flyway migrations against a real Postgres |

### E2E tests

| Command | Description |
|---------|-------------|
| `npm run e2e:smoke` | Playwright tests tagged `SMOKE` (fast, ~5 min) |
| `npm run e2e:full` | Full Playwright suite (all scenarios) |

> **Note:** E2E tests require the local stack to be running first
> (`./scripts/dev.sh --seed`).

### Security / compliance

| Command | Description |
|---------|-------------|
| `npm run audit` | `npm audit --audit-level=high` (workspace deps) |
| `npm run audit:e2e` | `npm audit --audit-level=high` (e2e deps) |
| `npm run openapi:diff` | Compare two OpenAPI specs for breaking changes |
| `./scripts/semgrep-scan.sh` | Local SAST scan (mirrors CI Gate 5) |
| `npm run sbom:frontend` | Generate frontend CycloneDX SBOM → `frontend-sbom.json` |

---

## CI Pipeline

The GitHub Actions pipeline (`.github/workflows/ci.yml`) enforces **7 gates**
that must all pass before a merge to `main`.

| Gate | Job | What it checks | Status |
|------|-----|----------------|--------|
| **1** | Static Analysis | TypeScript strict typecheck · ESLint (frontend) · Checkstyle (backend) | ✅ Implemented |
| **2** | Unit Tests | Vitest (frontend/contracts) · JUnit/Spring Boot (backend) | ✅ Implemented |
| **3** | Migration Compatibility | `PostgresSchemaCompatibilityTest` – Flyway migrations against real Postgres (Testcontainers) | ✅ Implemented |
| **4** | Contract Checks | OpenAPI generated types are up-to-date · Breaking-change detection vs `main` (`scripts/openapi-diff.sh`, backed by `oasdiff`) | ✅ Implemented |
| **5** | Security | `npm audit` · SAST via Semgrep (`auto` ruleset) · Secret detection via Gitleaks · npm license compliance check (GPL blocklist) | ✅ Implemented |
| **6** | Build | Full production build · Docker image build · Frontend + backend SBOM generation (CycloneDX) | ✅ Implemented |
| **7** | E2E Smoke | Playwright smoke suite against seeded local stack (Chromium) | ✅ Implemented |

Gates 1–5 run in parallel; Gate 6 depends on all of them; Gate 7 depends on
Gate 6.

---

## API Documentation

The REST API is documented as an **OpenAPI 3.1 specification**:

```
contracts/openapi.yaml
```

Key API groups:

| Tag | Description |
|-----|-------------|
| `Plans` | Weekly plan CRUD, lifecycle transitions (lock, reconcile, carry-forward) |
| `Commits` | Commitment CRUD with RCDO linking and chess classification |
| `Manager` | Team roll-up, alignment dashboard, review and approval |
| `RCDO` | Read-through proxy for Rally Cry / Defining Objective / Outcome hierarchy |
| `AI` | RCDO suggestion and reconciliation draft endpoints |
| `Notifications` | In-app notification read/dismiss |

To regenerate the TypeScript client after editing the spec:

```bash
cd contracts && npm run generate:client
```

The CI pipeline (Gate 4) verifies that generated types are committed and
uses `scripts/openapi-diff.sh` to detect breaking changes against the `main`
branch spec. The wrapper currently uses `oasdiff` because the repo contract is
OpenAPI 3.1.

---

## Observability

### Grafana Dashboards

Six dashboards are defined in `infra/observability/`:

| File | Dashboard | Key panels |
|------|-----------|------------|
| `grafana-service-health.json` | Service Health | API error rate, p95 latency, JVM heap, pod restarts |
| `grafana-business-metrics.json` | Business Metrics | Plan creation rate, lock/reconcile funnel, AI suggestion acceptance |
| `grafana-outbox-events.json` | Outbox Events | Outbox lag, failed events, DLQ depth, SQS throughput |
| `grafana-infrastructure.json` | Infrastructure | Postgres connections/WAL, Redis hit rate, ECS CPU/memory |
| `grafana-ai-operations.json` | AI Operations | LLM latency, token cost, suggestion acceptance rate, fallback rate |
| `grafana-deployment-tracker.json` | Deployment Tracker | Deployment frequency, lead time, MTTR, change failure rate |

### Alert Rules

Active Prometheus alert rules are defined in `infra/observability/alert-rules.yml`.
Each active alert links to a runbook in `docs/runbooks/`.

| Alert | Threshold | Runbook |
|-------|-----------|---------|
| `WC_API_ErrorRate_FastBurn` | 5xx error budget burn rate > 14.4× over 1h for 5 min | [R1](docs/runbooks/R1-api-error-rate.md) |
| `WC_API_ErrorRate_SlowBurn` | 5xx error budget burn rate > 3× over 6h for 15 min | [R1](docs/runbooks/R1-api-error-rate.md) |
| `WC_API_Latency_CRUD` | CRUD API p95 latency > 250 ms for 10 min | [R2](docs/runbooks/R2-api-latency.md) |
| `WC_API_Latency_Dashboard` | Dashboard API p95 latency > 500 ms for 10 min | [R2](docs/runbooks/R2-api-latency.md) |
| `WC_Outbox_Lag` | `outbox_unpublished_count > 100` for 5 min | [R3](docs/runbooks/R3-outbox-lag.md) |
| `WC_Outbox_Stall` | No outbox publishes for 10 min while unpublished events remain | [R3](docs/runbooks/R3-outbox-lag.md) |
| `WC_DB_ConnectionPool_High` | HikariCP utilization > 80% for 5 min | [R5](docs/runbooks/R5-database-health.md) |
| `WC_Redis_Unavailable` | Redis connection errors detected for 3 min | [R6](docs/runbooks/R6-redis-unavailable.md) |
| `WC_AI_TimeoutRate` | AI timeout rate > 20% over 15 min | [R7](docs/runbooks/R7-llm-timeout.md) |
| `WC_Auth_FailureSpike` | HTTP 403 rate > 10 requests/5 min for 5 min | [R10](docs/runbooks/R10-security-event.md) |

Runbooks [R4](docs/runbooks/R4-notification-delivery.md),
[R8](docs/runbooks/R8-llm-cost-anomaly.md), and
[R9](docs/runbooks/R9-ai-suggestion-quality.md) are written, but their matching
alert rules are not yet wired into `alert-rules.yml`.

### Structured Logging

All log entries include `correlationId` and `orgId` fields. The backend
exposes Prometheus metrics at `/actuator/prometheus`.

---

## Infrastructure

### Terraform Skeleton

`infra/terraform/main.tf` defines the AWS resource skeleton:

- **SQS** – `wc-{env}-plan-events` queue + DLQ (SSE enabled)
- **Variables** – region, environment, DB instance class, ECS CPU/memory,
  container image URI
- **Backend** – S3 remote state (configured per-environment via `envs/*.tfvars`)

> ⚠️ **Note:** The Terraform skeleton currently provisions SQS queues only.
> VPC, ECS cluster, RDS, ElastiCache, and related networking resources are
> scaffolded as variable definitions but **not yet instantiated** as resources.
> These require environment-specific provisioning before production deployment.

To plan/apply:

```bash
cd infra/terraform
terraform init -backend-config=envs/staging.tfvars
terraform plan -var-file=envs/staging.tfvars
terraform apply -var-file=envs/staging.tfvars
```

### Feature Flags

Feature flags are defined in `infra/flags/feature-flags.json` and evaluated
at runtime by the backend.

| Flag | Default | Description |
|------|---------|-------------|
| `wc.module.enabled` | `false` | Show WC module in PA host navigation |
| `wc.chess.strict` | `true` | Enforce chess piece limits at lock time |
| `wc.lock.auto` | `false` | Auto-transition plans to RECONCILING on schedule |
| `wc.ai.suggest` | `true` | Enable AI RCDO suggestions |
| `wc.ai.reconciliation` | `false` | Enable AI reconciliation drafts (beta) |
| `wc.ai.manager-insights` | `false` | Enable AI manager insight summaries (beta) |
| `wc.notifications.email` | `false` | Email notifications (post-MVP) |
| `wc.notifications.slack` | `false` | Slack notifications (post-MVP) |

Rollout phases: Phase 0 (internal ST6 dogfood) → Phase 1 (3–5 volunteer
teams) → Phase 2 (25% → 50% → 100% broad rollout).

### Local Infrastructure

The full local stack is managed via Docker Compose:

```bash
docker compose up -d   # start Postgres, Redis, LocalStack
docker compose down    # stop and remove containers
```

LocalStack initialises two SQS queues (`wc-local-plan-events` and its DLQ)
via `infra/localstack/init-aws.sh`.

---

## Architecture Decision Records

ADRs are stored in `docs/adrs/` (PRD §17.1). Every architectural decision
that changes a structural assumption must produce an ADR before implementation.

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](docs/adrs/ADR-001-read-replica.md) | Add read replica for manager dashboard | Proposed |
| [ADR-002](docs/adrs/ADR-002-cdc-outbox.md) | Replace outbox poller with Debezium CDC | Proposed |
| [ADR-003](docs/adrs/ADR-003-multi-channel-notifications.md) | Multi-channel notification delivery | Proposed |
| ADR-004 … ADR-016 | (See [ADR index](docs/adrs/README.md) for full list) | Not Yet Drafted |

See [docs/adrs/README.md](docs/adrs/README.md) for the complete index,
format template, and guiding principles.

---

## Known Gaps / Roadmap

This section transparently documents PRD requirements that are not yet fully
implemented, grouped by severity. Items recently closed by the ST6 sprint are
noted.

### ✅ Recently Addressed (ST6 sprint)

| Item | PRD ref | Notes |
|------|---------|-------|
| Audit hash chain with tamper detection | §14.7 | `JpaAuditService` computes SHA-256 chain; `AuditHashChainVerificationJob` verifies daily |
| Audit hash chain integrity verification job | §14.7 | Scheduled daily at 03:00 UTC, `audit_hash_chain_breaks_total` counter |
| Notification retention job | §14.7 | `NotificationRetentionJob` purges notifications older than configurable TTL |
| Idempotency key retention job | §14.7 | `IdempotencyKeyRetentionJob` purges expired idempotency keys |
| Grafana dashboards (all 6) | §14.3 | Service health, business metrics, outbox, infrastructure, AI ops, deployment |
| ADR directory with initial ADRs | §17.1 | ADR-001–003 drafted; index with ADR-004–016 scaffolded |
| OpenAPI breaking change detection (Gate 4) | §13.2 | `scripts/openapi-diff.sh` (using `oasdiff` for OpenAPI 3.1) + CI step comparing to `main` |
| SAST (Semgrep) and secret detection (Gitleaks) in CI (Gate 5) | §13.2 | Added to security-checks job |
| SBOM generation (Gate 6) | §13.3 | CycloneDX for npm (frontend-sbom.json) and Gradle (backend) |
| Accessibility testing infrastructure | §13.1 | `jest-axe` integrated in frontend unit tests |

### 🔴 Critical

| Gap | PRD ref | Notes |
|-----|---------|-------|
| Container image scanning (Trivy / ECR scan) | §14.8 | Requires container registry; deferred pending cloud provisioning |
| Container image signing (Sigstore/cosign) | §13.3 | Requires Sigstore infra; deferred |
| RLS integration tests (cross-tenant isolation) | §14.4 | Row-level security policies exist in migrations; automated verification test not yet written |

### 🟡 Significant

| Gap | PRD ref | Notes |
|-----|---------|-------|
| Property-based tests (jqwik) | §13.1 | Not yet implemented in backend |
| Fuzz testing | §13.1 | Not scoped for MVP |
| Visual regression tests (Percy / Chromatic) | §13.1 | Requires external service; deferred |
| Additional alert wiring for notification delivery / AI cost / AI quality | §14.2–§14.3 | Runbooks exist (R4/R8/R9), but matching Prometheus alerts are not yet active |
| Preview environments (PR deploys) | §13.2 | Requires ECS/cloud provisioning; deferred |
| Canary deployment and automated rollback | §12.10 | `scripts/rollback.sh` exists; automated canary analysis deferred |
| Load testing (50 concurrent users, 200 req/s) | §14.10 | Required before prod launch; deferred |
| Accessibility audit (WCAG 2.1 AA full) | §9 | `jest-axe` covers unit-level; full audit deferred |

### 🔵 Infrastructure

| Gap | PRD ref | Notes |
|-----|---------|-------|
| Terraform VPC, ECS, RDS, ElastiCache resources | §12.8 | Skeleton exists; actual resources require cloud account provisioning |
| CD pipeline (automated deploy on Gate 6 pass) | §13.2 | CI pipeline complete; CD to ECS deferred |
| AWS Secrets Manager integration | §12.8 | Designed; requires provisioned environment |
| Multi-AZ RDS + ElastiCache replica | §12 | Terraform variables scoped; deferred |
| Grafana / Prometheus deployment to production | §14.3 | Dashboard JSON files ready; deployment deferred |

### Deployment Readiness

See [docs/deployment-readiness-checklist.md](docs/deployment-readiness-checklist.md)
for the complete pre-production sign-off checklist (PRD §14.10).

---

## References

- [Product Requirements Document](docs/prd.md) – Full PRD (§1–18)
- [Deployment Readiness Checklist](docs/deployment-readiness-checklist.md)
- [OpenAPI Specification](contracts/openapi.yaml)
- [ADR Index](docs/adrs/README.md)
- [Runbooks](docs/runbooks/)
- [CI Pipeline](.github/workflows/ci.yml)
