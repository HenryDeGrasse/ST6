package com.weekly.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CapacityProfileService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Confidence level thresholds (LOW / MEDIUM / HIGH) based on weeks of data</li>
 *   <li>Average estimated and actual hours computation</li>
 *   <li>Estimation bias (actual / estimated ratio)</li>
 *   <li>Realistic weekly cap (p50 median of weekly actual totals)</li>
 *   <li>Per-category bias in the JSON blob</li>
 *   <li>Per-priority completion rates in the JSON blob</li>
 *   <li>Upsert behaviour (existing vs new profile)</li>
 *   <li>{@link CapacityProfileService#getProfile} delegation to the repository</li>
 * </ul>
 */
class CapacityProfileServiceTest {

    /**
     * Fixed clock anchored to 2026-03-22 02:00 UTC (a Sunday).
     * {@code LocalDate.now(clock).with(DayOfWeek.MONDAY)} → 2026-03-16.
     */
    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    /** Monday of the window end: the ISO-week start for 2026-03-22. */
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 3, 16);

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private CapacityProfileRepository profileRepository;
    private CapacityProfileService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        profileRepository = mock(CapacityProfileRepository.class);

        service = new CapacityProfileService(
                planRepository,
                commitRepository,
                actualRepository,
                profileRepository,
                new ObjectMapper(),
                FIXED_CLOCK);

        // Default: no pre-existing profile; save() returns its argument
        when(profileRepository.findByOrgIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(profileRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private WeeklyPlanEntity plan(LocalDate weekStart) {
        return new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, weekStart);
    }

    private WeeklyCommitEntity commit(
            UUID planId,
            CommitCategory category,
            ChessPriority priority,
            String estimatedHours) {
        WeeklyCommitEntity c = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Task");
        c.setCategory(category);
        c.setChessPriority(priority);
        if (estimatedHours != null) {
            c.setEstimatedHours(new BigDecimal(estimatedHours));
        }
        return c;
    }

    private WeeklyCommitActualEntity actual(
            UUID commitId,
            String actualHours,
            CompletionStatus status) {
        WeeklyCommitActualEntity a = new WeeklyCommitActualEntity(commitId, ORG_ID);
        if (actualHours != null) {
            a.setActualHours(new BigDecimal(actualHours));
        }
        a.setCompletionStatus(status);
        return a;
    }

    // ── computeProfile ────────────────────────────────────────────────────────

    @Nested
    class ComputeProfile {

        @Test
        void zeroWeeksOfDataReturnsLowConfidenceWithZeroAverages() {
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            assertEquals("LOW", result.getConfidenceLevel());
            assertEquals(0, result.getWeeksAnalyzed());
            assertEquals(new BigDecimal("0.0"), result.getAvgEstimatedHours());
            assertEquals(new BigDecimal("0.0"), result.getAvgActualHours());
            assertNull(result.getEstimationBias());
            assertEquals(new BigDecimal("0.0"), result.getRealisticWeeklyCap());
        }

        @Test
        void sixWeeksOfDataReturnsMediumConfidenceWithCorrectAverages() {
            List<WeeklyPlanEntity> plans = new ArrayList<>();
            List<WeeklyCommitEntity> commits = new ArrayList<>();
            List<WeeklyCommitActualEntity> actuals = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
                WeeklyPlanEntity p = plan(WINDOW_END.minusWeeks(i));
                plans.add(p);
                WeeklyCommitEntity c = commit(
                        p.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0");
                commits.add(c);
                actuals.add(actual(c.getId(), "8.0", CompletionStatus.DONE));
            }

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(plans);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(actuals);

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            assertEquals("MEDIUM", result.getConfidenceLevel());
            assertEquals(6, result.getWeeksAnalyzed());
            assertEquals(new BigDecimal("10.0"), result.getAvgEstimatedHours());
            assertEquals(new BigDecimal("8.0"), result.getAvgActualHours());
        }

        @Test
        void estimationBiasIsComputedAsActualDividedByEstimated() {
            // 4 weeks, each with est=10h and actual=12h → bias = 12/10 = 1.20
            List<WeeklyPlanEntity> plans = new ArrayList<>();
            List<WeeklyCommitEntity> commits = new ArrayList<>();
            List<WeeklyCommitActualEntity> actuals = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                WeeklyPlanEntity p = plan(WINDOW_END.minusWeeks(i));
                plans.add(p);
                WeeklyCommitEntity c = commit(
                        p.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0");
                commits.add(c);
                actuals.add(actual(c.getId(), "12.0", CompletionStatus.DONE));
            }

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(plans);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(actuals);

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            assertEquals(new BigDecimal("1.20"), result.getEstimationBias());
        }

        @Test
        void realisticWeeklyCapIsMedianP50OfWeeklyActualTotals() {
            // 4 weeks with actuals: 10, 20, 30, 40 → median = (20 + 30) / 2 = 25.0
            List<WeeklyPlanEntity> plans = new ArrayList<>();
            List<WeeklyCommitEntity> commits = new ArrayList<>();
            List<WeeklyCommitActualEntity> actuals = new ArrayList<>();

            String[] weekActuals = {"10.0", "20.0", "30.0", "40.0"};
            for (int i = 0; i < 4; i++) {
                WeeklyPlanEntity p = plan(WINDOW_END.minusWeeks(i));
                plans.add(p);
                WeeklyCommitEntity c = commit(
                        p.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0");
                commits.add(c);
                actuals.add(actual(c.getId(), weekActuals[i], CompletionStatus.DONE));
            }

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(plans);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(actuals);

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            assertEquals(new BigDecimal("25.0"), result.getRealisticWeeklyCap());
        }

        @Test
        @SuppressWarnings("unchecked")
        void categoryBiasDeliveryAndOperationsBiasesAreDifferent() throws Exception {
            // DELIVERY: est=10h, actual=15h → bias=1.50
            // OPERATIONS: est=10h, actual=8h  → bias=0.80
            WeeklyPlanEntity p = plan(WINDOW_END);
            WeeklyCommitEntity deliveryCommit = commit(
                    p.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0");
            WeeklyCommitEntity opsCommit = commit(
                    p.getId(), CommitCategory.OPERATIONS, ChessPriority.QUEEN, "10.0");

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(p));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(deliveryCommit, opsCommit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            actual(deliveryCommit.getId(), "15.0", CompletionStatus.DONE),
                            actual(opsCommit.getId(), "8.0", CompletionStatus.DONE)));

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            List<Map<String, Object>> categoryBias =
                    new ObjectMapper().readValue(result.getCategoryBiasJson(), List.class);

            Map<String, Object> deliveryEntry = null;
            Map<String, Object> opsEntry = null;
            for (Map<String, Object> entry : categoryBias) {
                if ("DELIVERY".equals(entry.get("category"))) {
                    deliveryEntry = entry;
                } else if ("OPERATIONS".equals(entry.get("category"))) {
                    opsEntry = entry;
                }
            }

            assertNotNull(deliveryEntry, "DELIVERY entry should be present in category bias JSON");
            assertNotNull(opsEntry, "OPERATIONS entry should be present in category bias JSON");

            double deliveryBias = ((Number) deliveryEntry.get("bias")).doubleValue();
            double opsBias = ((Number) opsEntry.get("bias")).doubleValue();
            assertEquals(1.50, deliveryBias, 0.001);
            assertEquals(0.80, opsBias, 0.001);
            assertTrue(deliveryBias > opsBias,
                    "DELIVERY bias should be higher than OPERATIONS bias");
        }

        @Test
        @SuppressWarnings("unchecked")
        void perPriorityCompletionRatesKingDoneRateHigherThanPawn() throws Exception {
            // KING commits all DONE; PAWN commit NOT_DONE
            WeeklyPlanEntity p = plan(WINDOW_END);
            WeeklyCommitEntity king1 = commit(
                    p.getId(), CommitCategory.DELIVERY, ChessPriority.KING, "5.0");
            WeeklyCommitEntity king2 = commit(
                    p.getId(), CommitCategory.DELIVERY, ChessPriority.KING, "5.0");
            WeeklyCommitEntity pawn = commit(
                    p.getId(), CommitCategory.DELIVERY, ChessPriority.PAWN, "5.0");

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(p));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(king1, king2, pawn));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            actual(king1.getId(), "5.0", CompletionStatus.DONE),
                            actual(king2.getId(), "5.0", CompletionStatus.DONE),
                            actual(pawn.getId(), "5.0", CompletionStatus.NOT_DONE)));

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 6);

            List<Map<String, Object>> priorityCompletion =
                    new ObjectMapper().readValue(result.getPriorityCompletionJson(), List.class);

            Map<String, Object> kingEntry = null;
            Map<String, Object> pawnEntry = null;
            for (Map<String, Object> entry : priorityCompletion) {
                if ("KING".equals(entry.get("priority"))) {
                    kingEntry = entry;
                } else if ("PAWN".equals(entry.get("priority"))) {
                    pawnEntry = entry;
                }
            }

            assertNotNull(kingEntry, "KING entry should be present in priority completion JSON");
            assertNotNull(pawnEntry, "PAWN entry should be present in priority completion JSON");

            double kingDoneRate = ((Number) kingEntry.get("doneRate")).doubleValue();
            double pawnDoneRate = ((Number) pawnEntry.get("doneRate")).doubleValue();
            assertEquals(1.0, kingDoneRate, 0.001);
            assertEquals(0.0, pawnDoneRate, 0.001);
            assertTrue(kingDoneRate > pawnDoneRate,
                    "KING done rate should be higher than PAWN done rate");
        }

        @Test
        void moreThanEightWeeksOfDataReturnsHighConfidence() {
            // 9 plans → weeksAnalyzed=9 → confidence "HIGH"
            List<WeeklyPlanEntity> plans = new ArrayList<>();
            List<WeeklyCommitEntity> commits = new ArrayList<>();
            List<WeeklyCommitActualEntity> actuals = new ArrayList<>();

            for (int i = 0; i < 9; i++) {
                WeeklyPlanEntity p = plan(WINDOW_END.minusWeeks(i));
                plans.add(p);
                WeeklyCommitEntity c = commit(
                        p.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0");
                commits.add(c);
                actuals.add(actual(c.getId(), "10.0", CompletionStatus.DONE));
            }

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(plans);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(actuals);

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 12);

            assertEquals("HIGH", result.getConfidenceLevel());
            assertEquals(9, result.getWeeksAnalyzed());
        }

        @Test
        void existingProfileIsUpdatedRatherThanCreatingNew() {
            CapacityProfileEntity existingProfile = new CapacityProfileEntity(ORG_ID, USER_ID);
            when(profileRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(existingProfile));
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            CapacityProfileEntity result = service.computeProfile(ORG_ID, USER_ID, 4);

            // The same object is mutated and saved – not a freshly constructed entity
            assertSame(existingProfile, result);
            verify(profileRepository).save(existingProfile);
        }
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Nested
    class GetProfile {

        @Test
        void returnsEmptyOptionalWhenNoProfileExists() {
            when(profileRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            Optional<CapacityProfileEntity> result = service.getProfile(ORG_ID, USER_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsExistingProfileFromRepository() {
            CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, USER_ID);
            profile.setConfidenceLevel("MEDIUM");
            when(profileRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile));

            Optional<CapacityProfileEntity> result = service.getProfile(ORG_ID, USER_ID);

            assertTrue(result.isPresent());
            assertSame(profile, result.get());
            assertEquals("MEDIUM", result.get().getConfidenceLevel());
        }
    }
}
