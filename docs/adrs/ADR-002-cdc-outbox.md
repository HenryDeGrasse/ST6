# ADR-002: Replace Outbox Poller with Debezium CDC

**Date:** 2026-03-18
**Status:** Proposed

## Context

The current architecture uses an application-level outbox poller to publish domain events from the `outbox_events` table to the message bus (SQS). The poller queries the table on a schedule and publishes any unpublished rows, then marks them as published.

**Trigger (per §17.3.2):** Outbox poller latency exceeds 5s p95 (from write to publish), or event volume exceeds 1,000 events/hour sustained, or the `outbox_events` table grows beyond 100K unpublished rows during peak.

Current scaling thresholds reference (§17.6):

| Metric | Current target | Soft limit | Hard limit |
|--------|---------------|------------|------------|
| Outbox events/hour | < 500 | 1,000 | 5,000 |
| Outbox publish latency (p95) | < 2s | 5s | 10s |

The application-level poller has inherent polling latency, creates periodic load on the primary database, and introduces a potential bottleneck as event volume grows. Change Data Capture (CDC) using Debezium reads directly from the Postgres Write-Ahead Log (WAL), providing near-real-time event capture with minimal additional database load.

## Decision

Deploy a Debezium CDC connector that reads the Postgres WAL and captures `INSERT` operations on the `outbox_events` table, publishing them directly to the message bus (SQS or Kafka).

Specifically:
- Debezium reads the Postgres WAL via a logical replication slot.
- Debezium captures inserts to `outbox_events` and publishes them to the message bus.
- The application-level outbox poller is disabled via `outbox.poller.enabled=false`.
- The `outbox_events` table schema remains identical (§9.3) — this is a deployment change, not a code change.
- Outbox event schema, consumer logic, at-least-once delivery guarantees, and idempotency requirements are all unchanged.

This is a deployment change only. No application code or API contracts change.

## Consequences

**Benefits:**
- Sub-second publish latency replacing polling-interval latency.
- Eliminates periodic polling load on the primary database.
- Scales to significantly higher event volumes before hitting capacity limits.
- Event contracts and consumer logic remain unchanged — consumers are unaffected.
- The outbox table schema is bus-agnostic; this migration path was designed from the start.

**Trade-offs:**
- Introduces a new operational dependency: the Debezium connector and its health must be monitored.
- Requires Postgres logical replication to be enabled on the RDS instance — requires PA platform team coordination.
- A replication slot must be managed carefully; an unconsumed slot will cause WAL accumulation and disk pressure on the primary.
- Debezium connector failure requires operational runbook and alerting. The fallback (re-enabling the poller) must be documented and tested.
- If migrating to Kafka (§17.7), Debezium can publish directly to Kafka, making this a stepping stone toward that path.

**Related:** This change is a prerequisite for high-throughput event processing but is independent of the notification service extraction (ADR-007). The two should not be run concurrently per the "one migration at a time" principle (§17.1).
