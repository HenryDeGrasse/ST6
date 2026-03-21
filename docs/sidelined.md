# Sidelined / Revisit Later Notes

> Notes captured from planning discussion on 2026-03-20.
> Purpose: preserve ideas, constraints, and open questions we want to revisit in later sessions.

## Current active focus (not sidelined)

These are the areas we want to keep actively exploring in the near term.
Each has a dedicated phase doc in [docs/phases/](phases/README.md):

- **Phase 1:** Quick Update Flow & User Model → [phase-1](phases/phase-1-quick-updates-and-user-model.md)
- **Phase 2:** Multi-Week Strategic Intelligence → [phase-2](phases/phase-2-multi-week-strategic-intelligence.md)
- **Phase 3:** RCDO Target Dates & Urgency Modeling → [phase-3](phases/phase-3-rcdo-target-dates-and-urgency.md)
- **Phase 4:** Capacity Planning & User Performance Model → [phase-4](phases/phase-4-capacity-and-forecasting.md)
- **Phase 5:** Predictive Intelligence & AI Manager Planning → [phase-5](phases/phase-5-predictive-intelligence-and-manager-planning.md)

---

## Sidelined for later exploration

### 1) AI eval, observability, and quality governance

**Status:** sidelined for a dedicated session

**Why it matters**
- We want a deep improvement here, not a shallow patch.
- This should likely cover AI quality evaluation, prompt/version tracking, observability, cost controls, and trust metrics.

**When we revisit**
- During a dedicated AI quality / eval session
- Likely before broader rollout or major AI expansion

**Remember to consider**
- Golden datasets for RCDO suggestion / reconciliation / manager insights
- Prompt and model versioning
- Acceptance / override / rejection metrics by feature
- Cost and latency budgets per AI workflow
- Drift detection over time
- Better dashboards and alerting for AI quality, not just uptime

---

### 2) Platform surface / external API / MCP-style access

**Status:** sidelined for later exploration

**Why it matters**
- Interesting longer-term direction, but not current priority.
- Could become important if other internal tools, copilots, or assistants need access to WC data and workflows.

**When we revisit**
- After core workflow intelligence is stronger
- After AI eval / governance is in better shape

**Remember to consider**
- Internal vs external API boundary
- Read-only vs write-capable agents
- Webhooks / event subscriptions
- MCP or similar tool interface for enterprise assistants
- Security, auditability, and tenant isolation implications

---

### 3) Multi-channel notifications

**Status:** sidelined for now, but explicitly worth revisiting

**Preferred starting point when resumed**
- **Start with email digests**

**Why email digests first**
- Lower interruption cost than chat pings
- Good for manager weekly summaries and review queues
- Easier way to test what information users actually value

**Later channels to consider**
- Slack digests
- Slack actionable reminders
- Event-driven notifications for urgent manager attention

**Remember to consider**
- Notification fatigue / batching rules
- Per-user preferences
- Quiet hours / cadence
- Digest vs interruptive message semantics
- AI-generated summaries vs raw counts

---

### 4) Production hardening / polish pass

**Status:** sidelined until polish / eval phase

**Why**
- Important, but better handled during a dedicated stabilization pass.

**Topics to revisit**
- RLS integration tests
- Container image scanning / signing
- Preview environments
- Canary rollout automation
- Load testing
- Accessibility audit
- Additional alert wiring

---

## Constraints and preferences to remember later

### Chess-rule governance
- Chess-piece meaning should stay consistent org-wide.
- Only admins, managers, or skip-level managers should be able to change chess-rule configuration.
- Teams should still have substantial autonomy over cadence semantics and local workflow timing.

### Team autonomy vs standardization
When revisiting workflow configurability, preserve this principle:
- **Standardize semantic meaning** where cross-team comparison matters (e.g. chess priorities)
- **Allow team flexibility** where working style varies (e.g. cadence, reminder timing, review rhythm)

---

## Open future topics parked for later

These were discussed and should remain visible, but are not immediate action items:

- Better multi-channel notification strategy after email digests
- Broader platform/API exploration after core intelligence is stronger
- Deep AI quality/eval/observability work in a dedicated session
- Hardening / deployment / rollout polish after intelligence features are better defined
