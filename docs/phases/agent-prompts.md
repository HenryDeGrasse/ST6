# Agent Prompts — Parallel Implementation

> Prompts for AI coding agents in duet sessions.
> Each agent has a clear scope, file ownership, and coordination rules.

---

## Pre-flight: coordination contracts

**Before launching any Wave 1 agents**, ensure these rules are communicated
to ALL agents:

### Migration ranges

| Agent | Migration numbers | Purpose |
|-------|-------------------|---------|
| Agent A (Phase 1) | V7, V8 | `user_update_patterns`, `user_model_snapshots` |
| Agent B (Phase 2) | V9 | Materialized views |
| Agent C (Phase 3) | V10 | `outcome_metadata` |
| Agent D (Phase 4) | V11, V12 | Alter `weekly_commits`/`weekly_commit_actuals`, `user_capacity_profiles` |

### Shared files — DO NOT EDIT in Wave 1

These files will be modified during Wave 2 integration. Wave 1 agents must
define new interfaces and implementations instead of editing these:

- `backend/weekly-service/src/main/java/com/weekly/ai/PromptBuilder.java`
- `backend/weekly-service/src/main/java/com/weekly/ai/DefaultAiSuggestionService.java`
- `backend/weekly-service/src/main/java/com/weekly/ai/DefaultPlanQualityService.java`
- `backend/weekly-service/src/main/java/com/weekly/ai/DefaultNextWorkSuggestionService.java`
- `backend/weekly-service/src/main/java/com/weekly/shared/ManagerInsightDataProvider.java`
- `contracts/openapi.yaml` (each agent writes a separate spec fragment; merged in Wave 2)

### New code goes in new packages

| Agent | Backend package | Frontend directory |
|-------|----------------|--------------------|
| Agent A | `com.weekly.quickupdate`, `com.weekly.usermodel` | `frontend/src/components/QuickUpdate/`, `frontend/src/hooks/useQuickUpdate.ts`, `frontend/src/hooks/useUserProfile.ts` |
| Agent B | `com.weekly.analytics` | `frontend/src/components/StrategicIntelligence/`, `frontend/src/hooks/useOutcomeCoverage.ts` |
| Agent C | `com.weekly.urgency` | `frontend/src/components/UrgencyIndicator/`, `frontend/src/hooks/useOutcomeMetadata.ts` |
| Agent D | `com.weekly.capacity` | `frontend/src/components/CapacityView/`, `frontend/src/hooks/useCapacity.ts` |

### OpenAPI approach

Each agent writes their endpoints as a standalone YAML fragment at
`contracts/fragments/{agent-name}.yaml`. Wave 2 integration merges them
into `contracts/openapi.yaml`.

---

## Wave 1: Independent cores (parallel)

---

### Agent A — Quick Update Flow & User Model

```
GOAL:
Build the rapid-fire check-in update flow and the foundational user model
for the Weekly Commitments platform.

READ FIRST:
- docs/phases/phase-1-quick-updates-and-user-model.md (your full spec)
- docs/phases/README.md (context on what exists)
- docs/phases/agent-prompts.md (coordination rules — read the pre-flight section)

CONTEXT:
The system already has structured check-ins via POST /commits/{commitId}/check-in
with ProgressStatus (ON_TRACK, AT_RISK, BLOCKED, DONE_EARLY) and free-text notes.
See:
- backend/weekly-service/src/main/java/com/weekly/plan/controller/CheckInController.java
- backend/weekly-service/src/main/java/com/weekly/plan/domain/ProgressEntryEntity.java
- backend/weekly-service/src/main/java/com/weekly/plan/domain/ProgressStatus.java
- backend/weekly-service/src/main/java/com/weekly/plan/service/DefaultCheckInService.java
- frontend/src/components/QuickCheckIn.tsx
- frontend/src/hooks/useCheckIn.ts

YOUR DELIVERABLES:

1. BACKEND — Batch check-in endpoint
   Package: com.weekly.quickupdate
   - QuickUpdateController: POST /api/v1/plans/{planId}/quick-update
     Accepts array of {commitId, status, note} and creates ProgressEntry
     records for each. Returns updated entries. Validates plan belongs to
     user, plan is in LOCKED or RECONCILING state.
   - QuickUpdateService: orchestrates batch creation, delegates to existing
     ProgressEntryRepository.

2. BACKEND — AI check-in option generation
   Package: com.weekly.quickupdate
   - CheckInOptionController: POST /api/v1/ai/check-in-options
     Accepts {commitId, currentStatus, lastNote, daysSinceLastCheckIn}.
     Returns AI-generated status options and progress note suggestions.
   - CheckInOptionService: builds prompt from commit context + user history,
     calls LlmClient, validates response. On failure returns empty options.
   - CheckInOptionPromptBuilder: separate from main PromptBuilder (DO NOT
     edit PromptBuilder.java). Builds messages using commit title, category,
     prior notes, and user patterns.

3. BACKEND — User update pattern tracking
   Package: com.weekly.usermodel
   - UserUpdatePatternEntity: JPA entity for user_update_patterns table.
   - UserUpdatePatternRepository: Spring Data JPA repository.
   - UserUpdatePatternService: records typed notes, increments frequency,
     returns top-N patterns for a user+category.

4. BACKEND — User model computation
   Package: com.weekly.usermodel
   - UserModelService: computes derived metrics from historical data.
     Reads from weekly_plans, weekly_commits, weekly_commit_actuals,
     progress_entries. Writes to user_model_snapshots.
   - UserModelComputeJob: @Scheduled job, runs weekly. Recomputes all
     active users' model snapshots.
   - UserProfileController: GET /api/v1/users/me/profile — returns the
     user's model snapshot.

5. MIGRATIONS
   - V7__user_update_patterns.sql — create user_update_patterns table
     with RLS policy
   - V8__user_model_snapshots.sql — create user_model_snapshots table
     with RLS policy

6. FRONTEND — Quick Update card flow
   - New component: frontend/src/components/QuickUpdate/QuickUpdateFlow.tsx
     Card-based UI: one commitment per card, binary status question,
     AI-generated note options, free-text fallback. Arrow-key or button
     navigation.
   - New hook: frontend/src/hooks/useQuickUpdate.ts — calls batch endpoint
     and AI options endpoint.
   - Integration point: add a "Quick Update" button to WeeklyPlanPage.tsx
     that opens the flow (this is the ONE existing file you may edit in
     the frontend — add only the button trigger, not the flow itself).

7. FRONTEND — User profile panel
   - New component: frontend/src/components/UserProfile/UserProfilePanel.tsx
   - New hook: frontend/src/hooks/useUserProfile.ts — calls GET /users/me/profile
   - Show on WeeklyPlanPage as a collapsible panel (similar to MyTrendsPanel).

8. OPENAPI FRAGMENT
   - Write your endpoints to contracts/fragments/agent-a-quick-update.yaml
   - Endpoints: POST /plans/{planId}/quick-update, POST /ai/check-in-options,
     GET /users/me/profile

9. TESTS
   - Unit tests for QuickUpdateService, CheckInOptionService, UserModelService
   - Unit tests for UserUpdatePatternService
   - Frontend tests for QuickUpdateFlow and UserProfilePanel
   - Integration test for batch check-in endpoint

DO NOT EDIT:
- PromptBuilder.java (create CheckInOptionPromptBuilder instead)
- DefaultAiSuggestionService.java
- openapi.yaml (use fragment file)
- Any migration numbered V1-V6 or V9+

FEATURE FLAG:
- Add "quickUpdate" and "userProfile" to the feature flag context
  (frontend/src/context/FeatureFlagContext.tsx — you may add new flag keys)
- Add flag entries to infra/flags/feature-flags.json
```

---

### Agent B — Multi-Week Strategic Intelligence

```
GOAL:
Build the multi-week descriptive and diagnostic intelligence layer for
the manager dashboard. This is the biggest unlock for manager visibility
into strategic patterns.

READ FIRST:
- docs/phases/phase-2-multi-week-strategic-intelligence.md (your full spec)
- docs/phases/README.md (context on what exists)
- docs/phases/agent-prompts.md (coordination rules — read the pre-flight section)

CONTEXT:
The manager dashboard already exists with a team summary grid, RCDO rollup,
and AI insights panel. See:
- frontend/src/pages/TeamDashboardPage.tsx
- frontend/src/hooks/useTeamDashboard.ts
- frontend/src/hooks/useAiManagerInsights.ts
- frontend/src/components/AiManagerInsightsPanel.tsx
- frontend/src/components/TeamSummaryGrid.tsx
- frontend/src/components/RcdoRollupPanel.tsx
- backend/weekly-service/src/main/java/com/weekly/shared/ManagerInsightDataProvider.java
- backend/weekly-service/src/main/java/com/weekly/plan/service/PlanManagerInsightDataProvider.java
- backend/weekly-service/src/main/java/com/weekly/ai/PromptBuilder.java (READ but do NOT edit)

The current ManagerInsightDataProvider already includes multi-week context:
carryForwardStreaks, outcomeCoverageTrends, lateLockPatterns, reviewTurnaroundStats.
Your job is to go DEEPER.

YOUR DELIVERABLES:

1. BACKEND — Materialized views
   Package: com.weekly.analytics
   - Migration V9__materialized_views.sql:
     * mv_outcome_coverage_weekly — per-outcome, per-week commit counts,
       contributor counts, high-priority counts. Source: weekly_commits
       JOIN weekly_plans. Filter: plans in LOCKED+ states.
     * mv_user_weekly_summary — per-user, per-week aggregate: total commits,
       strategic commits, carried commits, avg confidence, done count.
       Source: weekly_plans + weekly_commits + weekly_commit_actuals.
   - MaterializedViewRefreshJob: @Scheduled job, runs every 15 minutes.
     Calls REFRESH MATERIALIZED VIEW CONCURRENTLY for each view.

2. BACKEND — Analytics service
   Package: com.weekly.analytics
   - AnalyticsService: queries materialized views to produce structured
     time-series data.
   - Methods:
     * getOutcomeCoverageTimeline(orgId, outcomeId, weeks) → week-by-week
       commit count, contributor count, trend direction
     * getTeamCarryForwardHeatmap(orgId, managerId, weeks) → user × week
       matrix of carry-forward counts
     * getCategoryShiftAnalysis(orgId, managerId, weeks) → per-user
       category distribution change vs prior period
     * getEstimationAccuracyDistribution(orgId, managerId, weeks) →
       per-user confidence vs completion scatter data
   - AnalyticsController: GET /api/v1/analytics/outcome-coverage,
     GET /api/v1/analytics/carry-forward-heatmap,
     GET /api/v1/analytics/category-shifts,
     GET /api/v1/analytics/estimation-accuracy

3. BACKEND — Enhanced diagnostic data provider
   Package: com.weekly.analytics
   - DiagnosticDataProvider interface (in com.weekly.shared):
     Provides enriched context for diagnostic AI:
     * getCategoryShifts(orgId, managerId, weekStart, windowWeeks)
     * getPerUserOutcomeCoverage(orgId, managerId, weekStart, windowWeeks)
     * getBlockerFrequency(orgId, managerId, weekStart, windowWeeks)
   - AnalyticsDiagnosticDataProvider: implementation that queries the
     materialized views and progress_entries table.

4. BACKEND — Rule-based predictions
   Package: com.weekly.analytics
   - PredictionService: computes rule-based predictions per §2 Layer 3
     of the phase doc. Methods:
     * predictCarryForward(orgId, userId) → {likely: bool, confidence, reason}
     * predictLateLock(orgId, userId) → {likely: bool, confidence, reason}
     * predictCoverageDecline(orgId, outcomeId) → {likely: bool, confidence, reason}
   - PredictionController: GET /api/v1/analytics/predictions/{userId}

5. FRONTEND — Dashboard extensions
   - New component: frontend/src/components/StrategicIntelligence/OutcomeCoverageTimeline.tsx
     Displays per-outcome week-by-week coverage with trend arrows.
   - New component: frontend/src/components/StrategicIntelligence/CarryForwardHeatmap.tsx
     User × week colored grid.
   - New component: frontend/src/components/StrategicIntelligence/PredictionAlerts.tsx
     Shows rule-based predictions with confidence levels.
   - New hook: frontend/src/hooks/useAnalytics.ts — calls analytics endpoints
   - Integration: add these as new tab or collapsible sections on the
     TeamDashboardPage. You may add tab navigation to TeamDashboardPage.tsx
     but keep existing panels unchanged.

6. OPENAPI FRAGMENT
   - Write to contracts/fragments/agent-b-analytics.yaml

7. TESTS
   - Unit tests for AnalyticsService, PredictionService
   - Test materialized view refresh in integration test
   - Frontend tests for new dashboard components

DO NOT EDIT:
- PromptBuilder.java (Wave 2 will wire diagnostic data into prompts)
- ManagerInsightDataProvider.java (define DiagnosticDataProvider separately)
- DefaultAiSuggestionService.java
- openapi.yaml (use fragment file)
- Any migration numbered V1-V8 or V10+

FEATURE FLAG:
- Add "strategicIntelligence" and "predictions" to feature flags
```

---

### Agent C — RCDO Target Dates & Urgency Modeling

```
GOAL:
Add target-date awareness, progress tracking, and urgency modeling to the
RCDO outcome layer. Enable the system to answer "how urgent is this strategy?"
not just "is this work strategic?"

READ FIRST:
- docs/phases/phase-3-rcdo-target-dates-and-urgency.md (your full spec)
- docs/phases/README.md (context on what exists)
- docs/phases/agent-prompts.md (coordination rules — read the pre-flight section)

CONTEXT:
RCDO data is currently read from an upstream service via RcdoClient. The system
caches the tree in Redis. See:
- backend/weekly-service/src/main/java/com/weekly/rcdo/RcdoClient.java
- backend/weekly-service/src/main/java/com/weekly/rcdo/InMemoryRcdoClient.java
- backend/weekly-service/src/main/java/com/weekly/rcdo/RcdoTree.java
- backend/weekly-service/src/main/java/com/weekly/rcdo/RcdoController.java

Since WC doesn't own the upstream RCDO service, target-date metadata is stored
locally as an overlay.

YOUR DELIVERABLES:

1. BACKEND — Outcome metadata store
   Package: com.weekly.urgency
   - Migration V10__outcome_metadata.sql:
     * outcome_metadata table with: org_id, outcome_id (PK composite),
       target_date, progress_type (ACTIVITY|METRIC|MILESTONE),
       metric_name, target_value, current_value, unit,
       milestones (JSONB), progress_pct, urgency_band, last_computed_at.
     * RLS policy on org_id.
     * Index on (org_id, urgency_band) for dashboard queries.
   - OutcomeMetadataEntity: JPA entity.
   - OutcomeMetadataRepository: Spring Data JPA repository.

2. BACKEND — Urgency computation
   Package: com.weekly.urgency
   - UrgencyComputeService: computes urgency bands for all outcomes
     in an org. Implements the algorithm from phase-3 doc §3:
     * Metric-based progress: currentValue/targetValue
     * Activity-based progress: derived from weekly_commits coverage
       (query weeks of data, compute velocity)
     * Composite progress: weighted combination
     * Urgency band: ON_TRACK, NEEDS_ATTENTION, AT_RISK, CRITICAL, NO_TARGET
   - UrgencyComputeJob: @Scheduled, runs every 30 minutes. Recomputes
     urgency bands for all orgs.
   - StrategicSlackService: computes the recommended strategic focus floor
     per team based on outcome urgency. Returns a slack band
     (HIGH_SLACK, MODERATE_SLACK, LOW_SLACK, NO_SLACK) and the numeric floor.

3. BACKEND — API
   Package: com.weekly.urgency
   - OutcomeMetadataController:
     * GET /api/v1/outcomes/metadata — list all outcome metadata for org
     * GET /api/v1/outcomes/{outcomeId}/metadata — single outcome
     * PUT /api/v1/outcomes/{outcomeId}/metadata — create or update
       (admin/manager only)
     * PATCH /api/v1/outcomes/{outcomeId}/progress — update metric
       current_value or milestone status
   - UrgencyController:
     * GET /api/v1/outcomes/urgency-summary — all outcomes with urgency
       bands for the org
     * GET /api/v1/team/strategic-slack — returns current slack band and
       recommended strategic focus floor

4. BACKEND — Shared interface for downstream phases
   Create in com.weekly.shared:
   - UrgencyDataProvider interface:
     * getOutcomeUrgency(orgId, outcomeId) → UrgencyInfo
     * getOrgUrgencySummary(orgId) → List<UrgencyInfo>
     * getStrategicSlack(orgId, managerId) → SlackInfo
   This interface will be consumed by Phase 2 integration (diagnostic AI),
   Phase 4 (capacity feasibility), and Phase 5 (forecasting).
   - UrgencyInfo record: outcomeId, outcomeName, targetDate, progressPct,
     expectedProgressPct, urgencyBand, daysRemaining
   - SlackInfo record: slackBand, strategicFocusFloor, atRiskCount,
     criticalCount

5. FRONTEND — Urgency indicators
   - New component: frontend/src/components/UrgencyIndicator/UrgencyBadge.tsx
     Small colored badge (green/yellow/orange/red) for urgency bands.
   - New component: frontend/src/components/UrgencyIndicator/OutcomeProgressCard.tsx
     Shows target date, progress bar, urgency band, days remaining.
   - New component: frontend/src/components/UrgencyIndicator/StrategicSlackBanner.tsx
     Shows current slack level and recommended strategic focus floor.
   - New hook: frontend/src/hooks/useOutcomeMetadata.ts
   - Integration: add UrgencyBadge next to outcome names in RcdoRollupPanel
     (you may add the badge import to RcdoRollupPanel.tsx — minimal edit).
     Add StrategicSlackBanner to TeamDashboardPage (above team grid).

6. FRONTEND — Outcome metadata admin
   - New component: frontend/src/components/UrgencyIndicator/OutcomeMetadataEditor.tsx
     Form for setting target date, progress type, metric values, milestones.
     Only shown to admin/manager roles.
   - Add as a new tab "Outcome Targets" to AdminDashboardPage.tsx
     (you may add the tab entry — minimal edit to existing file).

7. OPENAPI FRAGMENT
   - Write to contracts/fragments/agent-c-urgency.yaml

8. TESTS
   - Unit tests for UrgencyComputeService (all urgency band paths)
   - Unit tests for StrategicSlackService
   - Unit tests for activity-based progress computation
   - Frontend tests for UrgencyBadge, OutcomeProgressCard, StrategicSlackBanner
   - Integration test for metadata CRUD endpoints

DO NOT EDIT:
- PromptBuilder.java (Wave 2 wires urgency into AI prompts)
- DefaultPlanQualityService.java (Wave 2 adds urgency-aware nudges)
- DefaultNextWorkSuggestionService.java (Wave 2 adds urgency boosting)
- openapi.yaml (use fragment file)
- Any migration numbered V1-V9 or V11+

FEATURE FLAG:
- Add "outcomeUrgency" and "strategicSlack" to feature flags
```

---

### Agent D — Capacity Planning & User Performance

```
GOAL:
Add estimated/actual hours tracking to commitments, build the capacity
profile computation, and create overcommitment detection and team
capacity views.

READ FIRST:
- docs/phases/phase-4-capacity-and-forecasting.md (your full spec)
- docs/phases/README.md (context on what exists)
- docs/phases/agent-prompts.md (coordination rules — read the pre-flight section)

CONTEXT:
The current commit entity has no hours fields. The actual entity has an
optional time_spent integer but it's not used consistently. See:
- backend/weekly-service/src/main/java/com/weekly/plan/domain/WeeklyCommitEntity.java
- backend/weekly-service/src/main/java/com/weekly/plan/domain/WeeklyCommitActualEntity.java
- backend/weekly-service/src/main/resources/db/migration/V1__initial_schema.sql
- contracts/openapi.yaml (WeeklyCommit and WeeklyCommitActual schemas)

YOUR DELIVERABLES:

1. BACKEND — Schema changes
   - Migration V11__capacity_fields.sql:
     * ALTER TABLE weekly_commits ADD COLUMN estimated_hours NUMERIC(5,1);
     * ALTER TABLE weekly_commit_actuals ADD COLUMN actual_hours NUMERIC(5,1);
   - Migration V12__user_capacity_profiles.sql:
     * CREATE TABLE user_capacity_profiles with: org_id, user_id (PK composite),
       weeks_analyzed, avg_estimated_hours, avg_actual_hours, estimation_bias,
       realistic_weekly_cap, category_bias_json (JSONB), priority_completion_json
       (JSONB), confidence_level, computed_at.
     * RLS policy on org_id.
   - Update WeeklyCommitEntity: add estimatedHours field (BigDecimal, nullable).
   - Update WeeklyCommitActualEntity: add actualHours field (BigDecimal, nullable).
   - Update the existing CommitRequest/CommitResponse DTOs to include
     estimatedHours. Update ActualRequest/ActualResponse to include actualHours.
     These DTOs live in com.weekly.plan.dto — you MAY edit them to add the
     new optional fields.

2. BACKEND — Capacity profile computation
   Package: com.weekly.capacity
   - CapacityProfileEntity: JPA entity for user_capacity_profiles.
   - CapacityProfileRepository: Spring Data JPA repository.
   - CapacityProfileService: computes capacity profiles from historical data.
     Methods:
     * computeProfile(orgId, userId, weeks) → CapacityProfile
       Queries weekly_commits + weekly_commit_actuals over the window.
       Computes: avg estimated, avg actual, estimation bias (actual/estimated),
       realistic weekly capacity (p50 of actual hours), per-category bias,
       per-priority completion rates.
     * getProfile(orgId, userId) → CapacityProfile (from cache/table)
   - CapacityComputeJob: @Scheduled, runs weekly on Sunday night.
     Recomputes all active users.

3. BACKEND — Overcommitment detection
   Package: com.weekly.capacity
   - OvercommitDetector: given a plan and user profile, detects overcommitment.
     * detectOvercommitment(plan, commits, profile) → OvercommitWarning
     * Adjusts estimates by per-category bias
     * Returns level (NONE, MODERATE, HIGH) with explanation
   - This should be callable from the plan quality check flow but does NOT
     edit DefaultPlanQualityService. Instead, create:
   - CapacityQualityProvider interface (in com.weekly.shared):
     * getOvercommitmentWarning(orgId, planId, userId) → Optional<OvercommitWarning>
     Wave 2 will wire this into DefaultPlanQualityService.

4. BACKEND — API
   Package: com.weekly.capacity
   - CapacityController:
     * GET /api/v1/users/me/capacity — returns the user's capacity profile
     * GET /api/v1/team/capacity?weekStart=X — manager view: team capacity
       summary (each member's estimated, adjusted, realistic, overcommit status)
   - EstimationCoachingController:
     * GET /api/v1/users/me/estimation-coaching?planId=X — post-reconciliation
       feedback: this week's estimated vs actual, rolling patterns, category tips

5. FRONTEND — Hour input fields
   - Edit frontend/src/components/CommitEditor.tsx: add optional "Estimated hours"
     numeric input field. Keep it simple and non-required. This is ONE existing
     file you may edit — add only the input field, don't restructure.
   - Edit frontend/src/components/ReconciliationView.tsx: add optional
     "Actual hours" numeric input in the reconciliation form. Same rule:
     minimal addition only.

6. FRONTEND — Capacity views
   - New component: frontend/src/components/CapacityView/OvercommitBanner.tsx
     Warning banner shown on plan page when overcommitment detected.
   - New component: frontend/src/components/CapacityView/EstimationCoaching.tsx
     Post-reconciliation card showing estimation accuracy feedback.
   - New component: frontend/src/components/CapacityView/TeamCapacityPanel.tsx
     Manager view: table of team members with est/adjusted/realistic/status.
   - New hook: frontend/src/hooks/useCapacity.ts

7. OPENAPI FRAGMENT
   - Write to contracts/fragments/agent-d-capacity.yaml
   - ALSO: document the changes to existing WeeklyCommit and WeeklyCommitActual
     schemas (new optional fields estimatedHours, actualHours) so Wave 2
     integration knows what to merge into the main spec.

8. TESTS
   - Unit tests for CapacityProfileService (estimation bias, realistic cap)
   - Unit tests for OvercommitDetector (NONE, MODERATE, HIGH paths)
   - Frontend tests for OvercommitBanner, EstimationCoaching, TeamCapacityPanel
   - Integration test for capacity profile computation with test data

DO NOT EDIT:
- PromptBuilder.java
- DefaultPlanQualityService.java (define CapacityQualityProvider instead)
- DefaultNextWorkSuggestionService.java
- openapi.yaml (use fragment file + document schema additions separately)
- Any migration numbered V1-V10

NOTE ON EXISTING SCHEMA:
weekly_commit_actuals already has a `time_spent INTEGER` column. Your new
`actual_hours NUMERIC(5,1)` is the replacement — do NOT remove time_spent
(backward compatibility), but actual_hours is the preferred field going forward.
Document this in your migration comment.

FEATURE FLAG:
- Add "capacityTracking" and "estimationCoaching" to feature flags
```

---

## Wave 2: Integration wiring (after Wave 1 completes)

---

### Agent E — Wire data providers into AI and cross-phase connections

```
GOAL:
Connect the independent cores built in Wave 1. Wire urgency data into
AI prompts, wire analytics into manager insights, wire capacity into
plan quality, and merge OpenAPI fragments into the main spec.

READ FIRST:
- docs/phases/agent-prompts.md (this file — understand what Wave 1 built)
- All phase docs in docs/phases/
- The code delivered by Agents A-D (review their new packages)
- contracts/fragments/*.yaml (all four fragment files)

CONTEXT:
Wave 1 built four independent packages:
- com.weekly.quickupdate + com.weekly.usermodel (Agent A)
- com.weekly.analytics (Agent B)
- com.weekly.urgency (Agent C)
- com.weekly.capacity (Agent D)

Each defined shared interfaces but did NOT wire them into existing services.
Your job is to connect everything.

YOUR DELIVERABLES:

1. OPENAPI MERGE
   - Merge all four fragment files from contracts/fragments/ into
     contracts/openapi.yaml. Add new paths, schemas, and tags.
   - Add estimatedHours/actualHours to existing WeeklyCommit and
     WeeklyCommitActual schemas.
   - Run npm run typecheck in contracts/ to verify generated types.

2. PROMPT BUILDER — Urgency context
   Edit PromptBuilder.java:
   - Add urgency context to buildManagerInsightsMessages():
     Include outcome urgency bands, target dates, progress percentages,
     and strategic slack in the context block.
   - Add urgency context to buildRcdoSuggestMessages():
     Boost AT_RISK/CRITICAL outcomes in the candidate context.

3. PLAN QUALITY — Urgency + capacity integration
   Edit DefaultPlanQualityService.java:
   - Inject UrgencyDataProvider (from Agent C's shared interface)
   - Inject CapacityQualityProvider (from Agent D's shared interface)
   - Add urgency-aware nudge: if strategic focus is below the slack floor
   - Add overcommitment nudge: if CapacityQualityProvider returns a warning

4. NEXT-WORK SUGGESTIONS — Urgency boosting
   Edit DefaultNextWorkSuggestionService.java:
   - Inject UrgencyDataProvider
   - In coverage gap suggestion scoring, multiply confidence by urgency
     multiplier (CRITICAL: 1.4, AT_RISK: 1.2, NEEDS_ATTENTION: 1.1)

5. MANAGER INSIGHTS — Diagnostic data
   Edit ManagerInsightDataProvider.java (or its implementation):
   - Add methods/fields for diagnostic context from Agent B's
     DiagnosticDataProvider
   - Wire into PlanManagerInsightDataProvider implementation

6. TRENDS — Capacity and urgency
   Edit DefaultTrendsService:
   - Include estimated vs actual hours in trend computation when available
   - Include user's capacity profile summary in trend insights

7. TESTS
   - Integration tests verifying urgency appears in AI prompts
   - Integration tests verifying capacity warnings appear in plan quality
   - Contract tests: regenerate TypeScript client from merged OpenAPI,
     verify typecheck passes
```

---

### Agent F — Phase 1 learning loop and AI option personalization

```
GOAL:
Build the learning loop that makes the quick-update AI options improve
over time based on user behavior. Also wire the user model into AI
suggestion flows.

READ FIRST:
- docs/phases/phase-1-quick-updates-and-user-model.md §2 "Learning mechanism"
- Review Agent A's delivered code in com.weekly.quickupdate and com.weekly.usermodel

CONTEXT:
Agent A built the quick-update flow, the user_update_patterns table, and
the user model computation. This agent makes the AI options smarter
over time.

YOUR DELIVERABLES:

1. LEARNING AGGREGATION JOB
   Package: com.weekly.usermodel
   - UpdatePatternAggregationJob: @Scheduled, runs nightly.
     Scans recent progress_entries, extracts typed notes (those not matching
     any AI-suggested option), groups by user+category, upserts into
     user_update_patterns with frequency counts.

2. PERSONALIZED OPTION GENERATION
   Edit Agent A's CheckInOptionService:
   - Before calling the LLM, load user's top-5 patterns for the commit's
     category from UserUpdatePatternService.
   - Include them in the prompt as "user's common responses."
   - Tag returned options with source: "user_history", "ai_generated",
     or "team_common" (options common across the team for this category).

3. USER MODEL → AI SUGGESTION ENRICHMENT
   Edit Agent A's CheckInOptionPromptBuilder:
   - Include user model summary (completion reliability, category strengths)
     in the system prompt for more contextual option generation.
   - E.g., if user has 92% DONE rate on Operations, AI should suggest more
     "completed" options for Operations commits.

4. TEAM COMMON PATTERNS
   - TeamPatternService: aggregates the most common update notes across
     all users in an org for each category. Used as fallback options
     when a user has no personal history.

5. TESTS
   - Unit tests for aggregation job
   - Unit tests verifying personalized options include user history
   - Test that team common patterns surface for new users
```

---

## Wave 3: Synthesis (after Wave 2 completes)

---

### Agent G — Predictive Intelligence & Planning Copilot

```
GOAL:
Build the predictive forecasting model, the manager planning copilot,
and the executive strategic health dashboard. This synthesizes all
prior phases into forward-looking intelligence.

READ FIRST:
- docs/phases/phase-5-predictive-intelligence-and-manager-planning.md (full spec)
- All other phase docs for context on available data
- Review all code delivered by Waves 1 and 2

CONTEXT:
After Waves 1-2, the system has:
- User model with performance/preference/capacity profiles
- Materialized views with multi-week analytics
- Outcome urgency bands with target dates and progress tracking
- Capacity profiles with estimation bias and realistic throughput
- Enhanced AI prompts with urgency and diagnostic context
- Rule-based predictions for carry-forward, late-lock, coverage decline

Your job is to build the synthesis layer.

YOUR DELIVERABLES:

1. BACKEND — Target date forecasting
   Package: com.weekly.forecast
   - TargetDateForecastService: computes probability of meeting target date
     per outcome. Uses:
     * Progress velocity from urgency service (Phase 3)
     * Coverage trend from analytics (Phase 2)
     * Team capacity from capacity profiles (Phase 4)
     * Carry-forward risk from predictions (Phase 2)
     Produces a composite score [0, 1] with contributing factors.
   - ForecastComputeJob: @Scheduled, runs daily. Recomputes forecasts for
     all outcomes with target dates.
   - ForecastController: GET /api/v1/outcomes/forecasts — all outcome forecasts
     GET /api/v1/outcomes/{outcomeId}/forecast — single outcome detail

2. BACKEND — Manager planning copilot
   Package: com.weekly.forecast
   - PlanningCopilotService: generates team allocation suggestions for a week.
     Inputs: team capacity profiles, outcome urgency summary, current coverage,
     carry-forward items, user model (strengths, patterns).
     Output: per-member suggested commits with rationale.
   - PlanningCopilotController:
     * POST /api/v1/ai/team-plan-suggestion — returns suggested allocation
     * POST /api/v1/ai/team-plan-suggestion/apply — creates draft plans
       for specified team members with suggested commits (requires each
       IC to review and lock their own plan — never auto-locks).

3. BACKEND — Executive dashboard data
   Package: com.weekly.forecast
   - ExecutiveDashboardService: aggregates strategic health across teams.
     * Rally Cry health summary (outcome forecasts rolled up)
     * Org-wide capacity utilization (strategic vs non-strategic)
     * Cross-team comparison (aggregate only, no individual data)
   - ExecutiveDashboardController:
     * GET /api/v1/executive/strategic-health
     * POST /api/v1/ai/executive-briefing — AI-generated narrative summary

4. BACKEND — Enhanced agents (PRD §17.4.3)
   Package: com.weekly.forecast
   - WeeklyPlanningAgentService: enhanced version of existing
     "start from last week" that is capacity-aware and urgency-aware.
     Runs as a scheduled job (Sunday evening per user timezone).
     Creates a notification with a link to the pre-drafted plan.
   - MisalignmentDetectorService: daily scan of locked plans.
     Compares allocation against urgency. Generates manager briefing.
     Writes to notifications table.

5. FRONTEND — Forecast views
   - New component: frontend/src/components/Forecast/OutcomeRiskCard.tsx
     Shows forecast probability, contributing factors, recommendation.
   - New component: frontend/src/components/Forecast/PlanningCopilot.tsx
     Manager tool: shows suggested allocation, allows modification,
     "Apply" button to create drafts.
   - New component: frontend/src/components/Forecast/ExecutiveHealth.tsx
     Rally Cry health grid with forecast badges.
   - New component: frontend/src/components/Forecast/ExecutiveBriefing.tsx
     AI-generated narrative with refresh button.
   - New hooks: useForecasts.ts, usePlanningCopilot.ts, useExecutiveDashboard.ts

6. FRONTEND — New executive page
   - New page: frontend/src/pages/ExecutiveDashboardPage.tsx
   - Add route and navigation entry (edit App.tsx / router config)

7. OPENAPI
   - Add all new endpoints directly to contracts/openapi.yaml
     (Wave 3 is sequential — no merge conflicts)

8. TESTS
   - Unit tests for TargetDateForecastService (all factor combinations)
   - Unit tests for PlanningCopilotService (allocation logic)
   - Unit tests for MisalignmentDetectorService
   - Frontend tests for all new components
   - Integration test for planning copilot end-to-end flow

FEATURE FLAGS:
- "forecasting", "planningCopilot", "executiveDashboard",
  "weeklyPlanningAgent", "misalignmentDetector"
```

---

## Summary: what to launch and when

```
WAVE 1 (parallel — 4 agents):
  Agent A: Quick Update + User Model
  Agent B: Multi-Week Strategic Intelligence
  Agent C: RCDO Target Dates + Urgency
  Agent D: Capacity Planning

WAVE 2 (parallel — 2 agents, after Wave 1):
  Agent E: Integration wiring + OpenAPI merge
  Agent F: Learning loop + AI personalization

WAVE 3 (sequential — 1 agent, after Wave 2):
  Agent G: Predictive Intelligence + Planning Copilot
```
