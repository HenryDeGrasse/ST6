# ADR-001: Add Read Replica for Manager Dashboard Queries

**Date:** 2026-03-18
**Status:** Proposed

## Context

The manager dashboard serves aggregated team alignment data via endpoints such as `/weeks/{w}/team/summary`, RCDO tree reads, and reporting queries. These are all read-heavy, analytical-style queries that run against the single primary Postgres instance.

**Trigger (per §17.3.1):** Manager dashboard p95 latency exceeds 400ms with teams of 30+ users, or the primary DB's read IOPS consistently exceeds 70% of provisioned capacity.

As the product scales, these read queries compete with write operations (plan CRUD, state transitions, reviews) on the same primary database. This creates a risk of read pressure degrading the write path, which is the more latency-sensitive path for ICs submitting plans and managers approving them.

Current scaling thresholds reference (§17.6):

| Metric | Current target | Soft limit | Hard limit |
|--------|---------------|------------|------------|
| Concurrent API requests | 50 req/s | 150 req/s | 300 req/s |
| Dashboard query latency (p95) | < 200ms | 400ms | 600ms |

## Decision

Add an RDS read replica in the same region and route all `GET` endpoints on the manager dashboard to the read replica via a Spring `@Transactional(readOnly = true)` routing datasource.

Specifically:
- All `GET` endpoints on the manager dashboard are routed to the read replica.
- Write operations (plan CRUD, state transitions, reviews) continue to hit the primary.
- Application code and API contracts remain unchanged — the read replica is a transparent infrastructure addition.
- For notification-driven drill-down flows (where read-your-writes semantics are required), individual plan reads are routed to the primary to avoid stale data.
- A "data may be up to a few seconds behind" banner is displayed on the dashboard to set user expectations around replication lag.

This is an infrastructure-only change. No API contracts or application logic changes are required.

## Consequences

**Benefits:**
- Reduces read IOPS pressure on the primary Postgres instance.
- Allows dashboard query performance to scale independently from the write path.
- Transparent to API consumers — no contract changes.
- Low implementation risk: Spring routing datasource is a well-understood pattern.

**Trade-offs:**
- Introduces replication lag (typically < 1s on RDS same-region replicas), which can cause managers to see a stale plan state immediately after an IC locks.
- Adds a new infrastructure dependency (RDS read replica) with associated cost and operational overhead.
- Requires coordination with the PA platform team for RDS read replica provisioning.
- Read-your-writes semantics must be explicitly handled for notification-driven flows — incomplete handling could lead to confusing UX.

**Related:** ADR-004 (materialized views for analytics) builds on this — the analytics read model can be hosted on the read replica to further offload the primary.
