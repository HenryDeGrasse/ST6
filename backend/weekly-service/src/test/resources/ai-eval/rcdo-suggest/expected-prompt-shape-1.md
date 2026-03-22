# Expected Prompt Shape: RCDO Suggest — Fixture 1

Describes the message structure produced by
`PromptBuilder.buildRcdoSuggestMessages(title, description, candidateOutcomes, topTeamOutcomes, zeroCoverageOutcomeNames, candidateUrgencies)`
when called with the data from `input-context-1.json`.
The candidate lists map to `PromptBuilder.CandidateOutcome`, `PromptBuilder.TeamOutcomeUsage`,
and `PromptBuilder.CandidateUrgencyContext` records.

The commit under evaluation is the **unlinked** commit:
- title: `"Draft onboarding email sequence for new trial users"`
- description: `"Write 3-email drip sequence for users who sign up for a trial but do not activate within 48 hours."`

---

## Message 1 — Role: SYSTEM

### Content (invariant system instructions)

```
You are an AI assistant that maps weekly commitments to strategic outcomes.
Given a commitment title and description, suggest the most relevant RCDO
(Rally Cry → Defining Objective → Outcome) mappings from the provided candidate list.

Rules:
1. ONLY suggest outcomes from the candidate list provided. Never invent IDs.
2. Return between 1 and 5 suggestions, ranked by confidence (highest first).
3. Each suggestion must include outcomeId, rallyCryName, objectiveName, outcomeName,
   confidence (0.0-1.0), and a brief rationale.
4. If no candidates are relevant, return an empty suggestions array.
5. Use the team context (if provided) to bias towards outcomes the team is actively
   working toward or outcomes that need more coverage.
6. When urgency context is provided, explicitly favour AT_RISK and CRITICAL outcomes
   over lower-urgency candidates when relevance is otherwise similar.
7. Respond ONLY with valid JSON matching the required schema.
```

### Invariants
- Role MUST be `SYSTEM`.
- Rules 1–7 MUST all be present verbatim.
- No user-authored text appears in this message.

---

## Message 2 — Role: ASSISTANT

### Structure

The ASSISTANT message is a multi-section plain-text block. All sections are machine-generated (not user-authored).

#### Section A: Available RCDO outcomes

One line per candidate outcome in the format:
```
- outcomeId: <id> | outcomeName: <name> | objectiveName: <name> | rallyCryName: <name>
```

For outcomes that have urgency data, the line is extended with:
```
 | urgencyBand: <band> | urgencyPreference: <FAVOR_HIGH_URGENCY|NORMAL> | targetDate: <date|n/a> | actualProgressPct: <pct|n/a> | expectedProgressPct: <pct|n/a> | daysRemaining: <days|no-target>
```

Expected lines for the 4 outcomes with urgency context in fixture 1:

```
- outcomeId: out-005 | outcomeName: Achieve sub-200ms P95 latency across all core APIs | objectiveName: Achieve top-tier reliability and performance | rallyCryName: Build a world-class platform | urgencyBand: AT_RISK | urgencyPreference: FAVOR_HIGH_URGENCY | targetDate: 2026-06-30 | actualProgressPct: 22 | expectedProgressPct: 35 | daysRemaining: 101
- outcomeId: out-007 | outcomeName: Increase trial-to-paid conversion rate from 18% to 25% | objectiveName: Improve activation and retention rates | rallyCryName: Grow and retain customers | urgencyBand: CRITICAL | urgencyPreference: FAVOR_HIGH_URGENCY | targetDate: 2026-04-30 | actualProgressPct: 10 | expectedProgressPct: 45 | daysRemaining: 40
- outcomeId: out-001 | outcomeName: Enable self-service data portability for all customers | objectiveName: Deliver enterprise-grade compliance features | rallyCryName: Win enterprise customers | urgencyBand: ON_TRACK | urgencyPreference: NORMAL | targetDate: 2026-09-30 | actualProgressPct: 40 | expectedProgressPct: 38 | daysRemaining: 193
- outcomeId: out-009 | outcomeName: Launch in-app onboarding checklist for new users | objectiveName: Improve activation and retention rates | rallyCryName: Grow and retain customers | urgencyBand: NEEDS_ATTENTION | urgencyPreference: NORMAL | targetDate: 2026-05-31 | actualProgressPct: 28 | expectedProgressPct: 40 | daysRemaining: 71
```

Outcomes **without** urgency context have NO urgency suffix: `out-002`, `out-003`, `out-004`, `out-006`, `out-008`.

#### Section B: Urgency preference guidance

Appended only when at least one outcome has `urgencyBand` of `AT_RISK` or `CRITICAL` (i.e., `isEscalated() == true`). In fixture 1, two outcomes qualify: `out-005` and `out-007`.

```
Urgency preference guidance:
- Achieve sub-200ms P95 latency across all core APIs is AT_RISK and should be explicitly favoured when the commitment could plausibly support it.
- Increase trial-to-paid conversion rate from 18% to 25% is CRITICAL and should be explicitly favoured when the commitment could plausibly support it.
```

#### Section C: Team usage context

Appended when `topTeamOutcomes` is non-empty (limit 5 entries):

```
Team usage context:
Top 5 outcomes your team linked to this week:
- Achieve sub-200ms P95 latency across all core APIs (5 commits)
- Enable self-service data portability for all customers (3 commits)
- Maintain 99.95% uptime SLA across all production services (2 commits)
- Increase trial-to-paid conversion rate from 18% to 25% (2 commits)
- Launch in-app onboarding checklist for new users (1 commits)
```

#### Section D: Zero-coverage outcomes

Appended when `zeroCoverageOutcomeNames` is non-empty (limit 10 entries):

```
Outcomes with 0 commits from your team this quarter:
- Achieve SOC 2 Type II certification by Q3
- Reduce average enterprise deal close time from 90 to 60 days
- Complete database sharding migration for user table
- Reduce 30-day churn rate from 8% to 5%
```

### Invariants
- Role MUST be `ASSISTANT`.
- No user-authored text appears in this message.
- Urgency section B is absent when no outcomes are escalated.
- Section C is absent when `topTeamOutcomes` is empty.
- Section D is absent when `zeroCoverageOutcomeNames` is empty.

---

## Message 3 — Role: USER

### Content

```
Commitment title: Draft onboarding email sequence for new trial users
Commitment description: Write 3-email drip sequence for users who sign up for a trial but do not activate within 48 hours.

Suggest the most relevant RCDO outcomes from the candidate list.
```

### Invariants
- Role MUST be `USER`.
- The title and description MUST be placed here (not in SYSTEM or ASSISTANT) to isolate user-authored text.
- The trailing directive line MUST be present.
- When `description` is blank/null, the `Commitment description:` line is OMITTED.

---

## Expected JSON Output Schema

Produced by `PromptBuilder.rcdoSuggestResponseSchema()`:

```json
{
  "type": "object",
  "required": ["suggestions"],
  "properties": {
    "suggestions": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["outcomeId", "rallyCryName", "objectiveName", "outcomeName", "confidence", "rationale"],
        "properties": {
          "outcomeId":      { "type": "string" },
          "rallyCryName":   { "type": "string" },
          "objectiveName":  { "type": "string" },
          "outcomeName":    { "type": "string" },
          "confidence":     { "type": "number", "minimum": 0, "maximum": 1 },
          "rationale":      { "type": "string" }
        }
      }
    }
  }
}
```

Validated into `AiSuggestionService.SuggestionResult(String status, List<AiSuggestionService.RcdoSuggestion> suggestions)`
with `AiSuggestionService.RcdoSuggestion(String outcomeId, String rallyCryName, String objectiveName, String outcomeName, double confidence, String rationale)`.

## Expected LLM Behavior (Fixture 1, Unlinked Commit)

For the commit `"Draft onboarding email sequence for new trial users"`:

- **Top suggestion** SHOULD be `out-007` ("Increase trial-to-paid conversion rate from 18% to 25%")
  because the commit is about activating trial users, and `out-007` is `CRITICAL` urgency.
- **Second suggestion** SHOULD be `out-009` ("Launch in-app onboarding checklist for new users")
  because both concern onboarding new/trial users.
- `out-008` ("Reduce 30-day churn rate") is plausible but lower-priority.
- `confidence` for top suggestion SHOULD be ≥ 0.80.
- `outcomeId` values MUST come from the candidate list (`out-001` through `out-009`). Never invented IDs.
