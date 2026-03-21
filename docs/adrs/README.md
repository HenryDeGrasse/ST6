# Architecture Decision Records (ADRs)

Architecture Decision Records are **first-class artifacts** in this project (§17.1). Every architectural decision that changes a structural assumption from the MVP must produce an ADR before implementation begins.

ADRs are stored in this directory (`docs/adrs/`), numbered sequentially, and follow the [Michael Nygard template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

## ADR Format Template

```markdown
# ADR-NNN: <Title>

**Date:** YYYY-MM-DD
**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-NNN

## Context

<Describe the situation, forces at play, and the problem that needs solving.>

## Decision

<Describe the change being proposed or the decision that was made.>

## Consequences

<Describe the resulting context after applying the decision — both positive and negative outcomes.>
```

## ADR Index

| ADR | Title | PRD Reference | Epic | Status |
|-----|-------|---------------|------|--------|
| [ADR-001](ADR-001-read-replica.md) | Add read replica for manager dashboard queries | §17.3.1 | WC-SCALE-001 | Proposed |
| [ADR-002](ADR-002-cdc-outbox.md) | Replace outbox poller with Debezium CDC | §17.3.2 | WC-SCALE-002 | Proposed |
| [ADR-003](ADR-003-multi-channel-notifications.md) | Multi-channel notification delivery | §17.3.3 | WC-NOTIFY-001 | Proposed |
| ADR-004 | Materialized views for analytics read model | §17.3.4 | WC-ANALYTICS-001 | Not Yet Drafted |
| ADR-005 | Skip-level read-only dashboard access | §17.3.5 | WC-ACCESS-001 | Not Yet Drafted |
| ADR-006 | Automated canary analysis and promotion | §17.3.6 | WC-CICD-001 | Not Yet Drafted |
| ADR-007 | Extract notification service | §17.4.1 | WC-ARCH-001 | Not Yet Drafted |
| ADR-008 | Cross-team and cross-org rollup architecture | §17.4.2 | WC-SCALE-003 | Not Yet Drafted |
| ADR-009 | Agentic AI workflow architecture | §17.4.3 | WC-AI-001, WC-AI-002, WC-AI-003 | Not Yet Drafted |
| ADR-010 | External tool integration adapter architecture | §17.4.4 | WC-INTEGRATE-001, WC-INTEGRATE-002 | Not Yet Drafted |
| ADR-011 | Capacity planning module | §17.4.5 | WC-CAPACITY-001 | Not Yet Drafted |
| ADR-012 | Multi-org federation architecture | §17.5.1 | WC-PLATFORM-001 | Not Yet Drafted |
| ADR-013 | Configurable workflow engine | §17.5.2 | WC-PLATFORM-002 | Not Yet Drafted |
| ADR-014 | Event-driven analytics pipeline | §17.5.3 | WC-DATA-001 | Not Yet Drafted |
| ADR-015 | Multi-region active-passive deployment | §17.5.4 | WC-INFRA-001 | Not Yet Drafted |
| ADR-016 | Public API and webhook platform | §17.5.5 | WC-PLATFORM-003 | Not Yet Drafted |

## Guiding Principles for Evolution (§17.1)

1. **Defer complexity until pain is measurable.** The modular monolith is the correct starting point. Extraction and new infrastructure are responses to observed bottlenecks, not anticipated ones.
2. **Preserve the seams.** Module boundaries, event contracts, and interface abstractions built in the MVP exist to make future extraction cheap.
3. **One migration at a time.** Never run two infrastructure migrations concurrently — each has its own rollback plan and stabilization window.
4. **ADRs as first-class artifacts.** Every architectural decision that changes a structural assumption must produce an ADR before implementation begins.
