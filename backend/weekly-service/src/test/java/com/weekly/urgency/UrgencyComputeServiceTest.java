package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link UrgencyComputeService}.
 *
 * <p>Follows {@code TrendsServiceTest} patterns: Mockito mocks, {@code @BeforeEach} setup,
 * and {@code @Nested} test classes that group tests by behaviour under test.
 *
 * <p>All date-arithmetic is anchored to a fixed clock at {@code 2026-03-20T12:00:00Z}
 * so every assertion is deterministic.  The Monday of that week is {@code 2026-03-16},
 * so the 8-week activity window runs {@code 2026-01-26 … 2026-03-16}.
 */
class UrgencyComputeServiceTest {

    private static final UUID ORG_ID     = UUID.randomUUID();
    private static final UUID OUTCOME_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    /** Fixed "now" used by the service under test. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-20T12:00:00Z"), ZoneOffset.UTC);

    /**
     * Monday of the week containing 2026-03-20.
     * {@code LocalDate.of(2026, 3, 20).with(DayOfWeek.MONDAY)} == 2026-03-16.
     */
    private static final LocalDate WINDOW_END   = LocalDate.of(2026, 3, 16);

    /** 8 weeks back from WINDOW_END: {@code 2026-03-16 − 7 weeks} == 2026-01-26. */
    private static final LocalDate WINDOW_START = LocalDate.of(2026, 1, 26);

    private OutcomeMetadataRepository metadataRepository;
    private WeeklyCommitRepository    commitRepository;
    private WeeklyPlanRepository      planRepository;
    private UrgencyComputeService     service;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(OutcomeMetadataRepository.class);
        commitRepository   = mock(WeeklyCommitRepository.class);
        planRepository     = mock(WeeklyPlanRepository.class);
        RcdoClient rcdoClient = mock(RcdoClient.class);

        service = new UrgencyComputeService(
                metadataRepository,
                commitRepository,
                planRepository,
                rcdoClient,
                new ObjectMapper(),
                FIXED_CLOCK);

        // Default: no plans in the activity window → activityScore = 0.0 for all tests
        // that do not override this stub.
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), any(), any())).thenReturn(List.of());
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Builds a metadata entity with a specific {@code createdAt} timestamp. */
    private OutcomeMetadataEntity makeMetadata(Instant createdAt) {
        OutcomeMetadataEntity metadata = new OutcomeMetadataEntity(ORG_ID, OUTCOME_ID);
        ReflectionTestUtils.setField(metadata, "createdAt", createdAt);
        return metadata;
    }

    /** Builds a LOCKED plan for {@code (ORG_ID, USER_ID)} at the given week. */
    private WeeklyPlanEntity makeLockedPlan(UUID planId, LocalDate weekStart) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
        plan.lock(LockType.ON_TIME);
        return plan;
    }

    /** Builds a commit linked to {@code outcomeId} inside {@code planId}. */
    private WeeklyCommitEntity makeCommitForOutcome(UUID planId, UUID outcomeId) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Task");
        commit.setOutcomeId(outcomeId);
        return commit;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Urgency band computation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tests all urgency-band paths defined in the phase-3 spec §3 algorithm.
     *
     * <p>All tests call {@link UrgencyComputeService#computeUrgencyBand} directly with
     * a pre-set {@code progressPct} on the entity, so no repository mocking is needed.
     */
    @Nested
    class UrgencyBandComputation {

        // ── NO_TARGET ──────────────────────────────────────────────────────

        /**
         * 1. NO_TARGET — {@code targetDate} is null → band is {@code NO_TARGET}.
         */
        @Test
        void returnsNoTargetWhenTargetDateIsNull() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            // targetDate deliberately left null

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_NO_TARGET, band);
        }

        // ── CRITICAL (due / past-due) ──────────────────────────────────────

        /**
         * 2a. CRITICAL — target date is in the past (yesterday).
         */
        @Test
        void returnsCriticalWhenTargetDateIsInThePast() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 3, 19)); // yesterday

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_CRITICAL, band);
        }

        /**
         * 2b. CRITICAL — target date is exactly today (due today).
         */
        @Test
        void returnsCriticalWhenTargetDateIsToday() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-03-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 3, 20)); // today
            metadata.setProgressPct(new BigDecimal("100.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_CRITICAL, band);
        }

        // ── ON_TRACK ───────────────────────────────────────────────────────

        /**
         * 3. ON_TRACK — actual progress ahead of schedule (gap &lt; 0).
         *
         * <p>Dates: createdAt=2026-01-01, targetDate=2026-06-30, today=2026-03-20.
         * daysTotal=180, daysElapsed=78, expectedProgress=0.433.
         * progressPct=60% → actualProgress=0.60 → gap=−0.167 &lt; 0.10 → ON_TRACK.
         */
        @Test
        void returnsOnTrackWhenProgressAheadOfSchedule() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 6, 30));
            metadata.setProgressPct(new BigDecimal("60.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_ON_TRACK, band);
        }

        /**
         * 4. ON_TRACK — progress within 10% of expected (gap 0–0.10).
         *
         * <p>Same date setup: expectedProgress=0.433, progressPct=38% →
         * gap=0.053 &lt; 0.10 → ON_TRACK.
         */
        @Test
        void returnsOnTrackWhenGapIsWithinTenPercent() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 6, 30));
            metadata.setProgressPct(new BigDecimal("38.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_ON_TRACK, band);
        }

        // ── NEEDS_ATTENTION ────────────────────────────────────────────────

        /**
         * 5. NEEDS_ATTENTION — gap between 0.10 and 0.25.
         *
         * <p>expectedProgress=0.433, progressPct=25% → gap=0.183 → NEEDS_ATTENTION.
         */
        @Test
        void returnsNeedsAttentionWhenGapBetween10And25Percent() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 6, 30));
            metadata.setProgressPct(new BigDecimal("25.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_NEEDS_ATTENTION, band);
        }

        /**
         * NEEDS_ATTENTION wins over the near-deadline CRITICAL rule when gap &lt; 0.25.
         *
         * <p>createdAt=2026-03-01, targetDate=2026-04-10, daysRemaining=21 &lt; 30.
         * daysTotal=40, daysElapsed=19, expectedProgress=0.475,
         * progressPct=30% → gap=0.175 &lt; 0.25 → NEEDS_ATTENTION (gap evaluated first).
         */
        @Test
        void returnsNeedsAttentionEvenWhenNearDeadlineRuleWouldApply() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-03-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 4, 10));
            metadata.setProgressPct(new BigDecimal("30.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_NEEDS_ATTENTION, band);
        }

        // ── AT_RISK ────────────────────────────────────────────────────────

        /**
         * 6. AT_RISK — gap &gt; 0.25 but daysRemaining ≥ 30 (near-deadline rule does not fire).
         *
         * <p>expectedProgress=0.433, progressPct=10% → gap=0.333 ≥ 0.25,
         * daysRemaining=102 ≥ 30 → AT_RISK.
         */
        @Test
        void returnsAtRiskWhenGapExceedsTwentyFivePercent() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 6, 30));
            metadata.setProgressPct(new BigDecimal("10.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_AT_RISK, band);
        }

        // ── CRITICAL (near deadline) ───────────────────────────────────────

        /**
         * 7. CRITICAL — daysRemaining &lt; 30 AND actualProgress &lt; 50%.
         *
         * <p>createdAt=2026-02-01, targetDate=2026-04-01, today=2026-03-20.
         * daysTotal=59, daysElapsed=47, expectedProgress=0.797,
         * progressPct=30% → gap=0.497 ≥ 0.25.
         * daysRemaining=12 &lt; 30 AND actualProgress=0.30 &lt; 0.50 → CRITICAL.
         */
        @Test
        void returnsCriticalWhenDaysRemainingLt30AndProgressLt50Pct() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-02-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 4, 1));
            metadata.setProgressPct(new BigDecimal("30.00"));

            String band = service.computeUrgencyBand(metadata);

            assertEquals(UrgencyComputeService.BAND_CRITICAL, band);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Progress computation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tests the three progress-signal models and their composite weightings.
     *
     * <p>Helper method {@link #stubFourLockedPlansWithTwoOutcomeCommits()} sets up
     * 4 locked plans in the activity window where only the 2 most-recent plans have
     * commits for {@code OUTCOME_ID}:
     * <ul>
     *   <li>coverageRatio = 2/4 = 0.50</li>
     *   <li>velocityBonus = 0.10 (recent half covered, earlier half not)</li>
     *   <li>activityScore = 0.60</li>
     * </ul>
     */
    @Nested
    class ProgressComputation {

        private static final UUID PLAN_ID_1 = UUID.randomUUID();
        private static final UUID PLAN_ID_2 = UUID.randomUUID();
        private static final UUID PLAN_ID_3 = UUID.randomUUID();
        private static final UUID PLAN_ID_4 = UUID.randomUUID();

        /**
         * Overrides the default planRepository stub with 4 locked plans;
         * stubs commitRepository so that plans 3 and 4 (the recent half)
         * have commits for {@code OUTCOME_ID}.
         *
         * <p>Resulting activityScore = clamp(0.50 + 0.10) = 0.60.
         */
        private void stubFourLockedPlansWithTwoOutcomeCommits() {
            WeeklyPlanEntity plan1 = makeLockedPlan(PLAN_ID_1, LocalDate.of(2026, 1, 26));
            WeeklyPlanEntity plan2 = makeLockedPlan(PLAN_ID_2, LocalDate.of(2026, 2,  2));
            WeeklyPlanEntity plan3 = makeLockedPlan(PLAN_ID_3, LocalDate.of(2026, 2,  9));
            WeeklyPlanEntity plan4 = makeLockedPlan(PLAN_ID_4, LocalDate.of(2026, 2, 16));

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, WINDOW_START, WINDOW_END))
                    .thenReturn(List.of(plan1, plan2, plan3, plan4));

            // Commits: plans 3 + 4 are linked to OUTCOME_ID; plan 1 has an unrelated commit
            WeeklyCommitEntity c3       = makeCommitForOutcome(PLAN_ID_3, OUTCOME_ID);
            WeeklyCommitEntity c4       = makeCommitForOutcome(PLAN_ID_4, OUTCOME_ID);
            WeeklyCommitEntity unrelated = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, PLAN_ID_1, "unrelated");

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(c3, c4, unrelated));
        }

        // ── METRIC progress ────────────────────────────────────────────────

        /**
         * 8. METRIC progress — currentValue / targetValue blended with activity 0.6/0.4.
         *
         * <p>No plans → activityScore = 0.0.
         * currentValue=80, targetValue=100 → metricScore=0.80.
         * composite = 0.80×0.6 + 0.0×0.4 = 0.48 → 48.00%.
         */
        @Test
        void computesMetricProgressAsCurrentOverTarget() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setProgressType("METRIC");
            metadata.setTargetValue(new BigDecimal("100"));
            metadata.setCurrentValue(new BigDecimal("80"));

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("48.00"), pct);
        }

        // ── MILESTONE progress ─────────────────────────────────────────────

        /**
         * 9. MILESTONE progress — JSONB milestones parsed; DONE = full weight,
         * IN_PROGRESS = half weight.
         *
         * <p>Milestones: DONE(2.0), IN_PROGRESS(2.0), TODO(2.0) → totalWeight=6.0,
         * completedWeight = 2.0 + 1.0 = 3.0 → milestoneScore = 0.50.
         * No plans → activityScore = 0.0.
         * composite = 0.50×0.5 + 0.0×0.5 = 0.25 → 25.00%.
         */
        @Test
        void computesMilestoneProgressWithDoneAndInProgress() {
            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setProgressType("MILESTONE");
            metadata.setMilestones(
                    "[{\"status\":\"DONE\",\"weight\":2.0},"
                    + "{\"status\":\"IN_PROGRESS\",\"weight\":2.0},"
                    + "{\"status\":\"TODO\",\"weight\":2.0}]");

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("25.00"), pct);
        }

        // ── ACTIVITY progress ──────────────────────────────────────────────

        /**
         * 10. ACTIVITY progress — mock commit data with a velocity bonus.
         *
         * <p>4 locked plans; plans 3+4 (recent half) covered → coverageRatio=0.50,
         * velocityBonus=0.10 → activityScore=0.60 → 60.00%.
         */
        @Test
        void computesActivityProgressWithVelocityBonus() {
            stubFourLockedPlansWithTwoOutcomeCommits();

            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setProgressType("ACTIVITY");

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("60.00"), pct);
        }

        // ── Composite METRIC + ACTIVITY (0.6 / 0.4) ───────────────────────

        /**
         * 11. Composite METRIC + ACTIVITY — weighted 60 % metric, 40 % activity.
         *
         * <p>currentValue=80, targetValue=100 → metricScore=0.80.
         * activityScore=0.60 (from stub).
         * composite = 0.80×0.6 + 0.60×0.4 = 0.48 + 0.24 = 0.72 → 72.00%.
         */
        @Test
        void computesCompositeMetricPlusActivityWith6040Weighting() {
            stubFourLockedPlansWithTwoOutcomeCommits();

            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setProgressType("METRIC");
            metadata.setTargetValue(new BigDecimal("100"));
            metadata.setCurrentValue(new BigDecimal("80"));

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("72.00"), pct);
        }

        // ── Composite MILESTONE + ACTIVITY (0.5 / 0.5) ────────────────────

        /**
         * 12. Composite MILESTONE + ACTIVITY — weighted 50 % milestone, 50 % activity.
         *
         * <p>Milestones: DONE(1.0), IN_PROGRESS(1.0) → totalWeight=2.0,
         * completedWeight=1.0+0.5=1.5 → milestoneScore=0.75.
         * activityScore=0.60 (from stub).
         * composite = 0.75×0.5 + 0.60×0.5 = 0.375 + 0.30 = 0.675 → 67.50%.
         */
        @Test
        void computesCompositeMilestonePlusActivityWith5050Weighting() {
            stubFourLockedPlansWithTwoOutcomeCommits();

            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            metadata.setProgressType("MILESTONE");
            metadata.setMilestones(
                    "[{\"status\":\"DONE\",\"weight\":1.0},"
                    + "{\"status\":\"IN_PROGRESS\",\"weight\":1.0}]");

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("67.50"), pct);
        }

        // ── Pure ACTIVITY (null progressType) ─────────────────────────────

        /**
         * 13. Pure ACTIVITY — null {@code progressType} defaults to the ACTIVITY signal.
         *
         * <p>activityScore=0.60 → 60.00%.
         */
        @Test
        void pureActivityProgressUsedWhenProgressTypeIsNull() {
            stubFourLockedPlansWithTwoOutcomeCommits();

            OutcomeMetadataEntity metadata = makeMetadata(Instant.parse("2026-01-01T00:00:00Z"));
            ReflectionTestUtils.setField(metadata, "progressType", null);

            BigDecimal pct = service.computeProgressPct(metadata);

            assertEquals(new BigDecimal("60.00"), pct);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. computeUrgencyForOutcome – single-outcome recomputation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ComputeForOutcome {

        /**
         * Full recomputation: loads, recomputes, and saves a single outcome.
         *
         * <p>No plans → activityScore=0.0 → progressPct=0.00%.
         * createdAt=2026-03-01, targetDate=2026-04-30.
         * daysTotal=60, daysElapsed=19, expectedProgress=0.317.
         * gap=0.317 ≥ 0.25; daysRemaining=41 ≥ 30 → AT_RISK.
         */
        @Test
        void recomputesSingleOutcomeAndSavesUrgencyBand() {
            OutcomeMetadataEntity metadata = new OutcomeMetadataEntity(ORG_ID, OUTCOME_ID);
            ReflectionTestUtils.setField(metadata, "createdAt",
                    Instant.parse("2026-03-01T00:00:00Z"));
            metadata.setTargetDate(LocalDate.of(2026, 4, 30));

            when(metadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.of(metadata));
            when(metadataRepository.save(metadata)).thenReturn(metadata);

            OutcomeMetadataEntity saved =
                    service.computeUrgencyForOutcome(ORG_ID, OUTCOME_ID).orElseThrow();

            assertEquals(new BigDecimal("0.00"), saved.getProgressPct());
            assertEquals(UrgencyComputeService.BAND_AT_RISK, saved.getUrgencyBand());
            verify(metadataRepository).save(metadata);
        }

        /**
         * Returns {@link Optional#empty()} when no metadata row exists for the given outcome.
         */
        @Test
        void returnsEmptyWhenOutcomeNotFound() {
            when(metadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.empty());

            Optional<OutcomeMetadataEntity> result =
                    service.computeUrgencyForOutcome(ORG_ID, OUTCOME_ID);

            assertFalse(result.isPresent());
        }

        /**
         * The activity window is aligned to Monday and covers exactly 8 weeks.
         *
         * <p>Verifies that {@link WeeklyPlanRepository#findByOrgIdAndWeekStartDateBetween}
         * is called with {@code [2026-01-26, 2026-03-16]}.
         */
        @Test
        void activityWindowQueriesCorrectMondayAlignedRange() {
            double score = service.computeActivityProgress(ORG_ID, OUTCOME_ID);

            assertEquals(0.0, score);
            verify(planRepository).findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, WINDOW_START, WINDOW_END);
        }

        /**
         * Returns zero when there are locked plans but none have commits for the outcome.
         */
        @Test
        void returnsZeroActivityWhenNoCommitsReferenceOutcome() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = makeLockedPlan(planId, LocalDate.of(2026, 2, 9));

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, WINDOW_START, WINDOW_END))
                    .thenReturn(List.of(plan));

            // Commit exists but for a different outcome
            WeeklyCommitEntity c = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Task");
            c.setOutcomeId(UUID.randomUUID()); // different outcome

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(c));

            double score = service.computeActivityProgress(ORG_ID, OUTCOME_ID);

            assertEquals(0.0, score);
        }
    }
}
