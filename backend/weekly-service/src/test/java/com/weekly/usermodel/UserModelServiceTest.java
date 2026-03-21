package com.weekly.usermodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link UserModelService}.
 */
class UserModelServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private ProgressEntryRepository progressEntryRepository;
    private UserModelSnapshotRepository snapshotRepository;
    private ObjectMapper objectMapper;
    private UserModelService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        snapshotRepository = mock(UserModelSnapshotRepository.class);
        objectMapper = new ObjectMapper();
        service = new UserModelService(
                planRepository,
                commitRepository,
                actualRepository,
                progressEntryRepository,
                snapshotRepository,
                objectMapper
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WeeklyPlanEntity makePlan(UUID planId, PlanState state) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                planId, ORG_ID, USER_ID, LocalDate.now(ZoneOffset.UTC).minusWeeks(1));
        plan.setState(state);
        return plan;
    }

    private WeeklyCommitEntity makeCommit(
            UUID commitId,
            UUID planId,
            ChessPriority priority,
            CommitCategory category,
            BigDecimal confidence,
            UUID carriedFrom
    ) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, planId, "Commit");
        commit.setChessPriority(priority);
        commit.setCategory(category);
        commit.setConfidence(confidence);
        commit.setCarriedFromCommitId(carriedFrom);
        return commit;
    }

    private WeeklyCommitActualEntity makeActual(UUID commitId, CompletionStatus status) {
        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commitId, ORG_ID);
        actual.setCompletionStatus(status);
        return actual;
    }

    /**
     * Stubs all four data repositories for a simple compute-snapshot invocation.
     * The snapshot repository save stub is also configured here.
     */
    private void stubComputeRepositories(
            List<WeeklyPlanEntity> plans,
            List<WeeklyCommitEntity> commits,
            List<WeeklyCommitActualEntity> actuals
    ) {
        when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                any(), any(), any(), any()))
                .thenReturn(plans);

        if (!plans.isEmpty()) {
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(any(), any()))
                    .thenReturn(commits);
        }

        if (!commits.isEmpty()) {
            when(actualRepository.findByOrgIdAndCommitIdIn(any(), any()))
                    .thenReturn(actuals);
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(any(), any()))
                    .thenReturn(List.of());
        }

        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── @Nested ComputeSnapshot ───────────────────────────────────────────────

    @Nested
    class ComputeSnapshot {

        /**
         * 3 commits with confidences [0.8, 0.6, 0.9]; 2 DONE + 1 PARTIALLY actual.
         * estimationAccuracy = sum(DONE confidences) / total reconciled
         *                    = (0.8 + 0.6) / 3 ≈ 0.4667
         */
        @Test
        void computesCorrectEstimationAccuracy() throws Exception {
            UUID planId = UUID.randomUUID();
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            UUID c3 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(planId, PlanState.RECONCILED);
            WeeklyCommitEntity commit1 = makeCommit(
                    c1, planId, ChessPriority.KING, CommitCategory.DELIVERY,
                    new BigDecimal("0.8"), null);
            WeeklyCommitEntity commit2 = makeCommit(
                    c2, planId, ChessPriority.QUEEN, CommitCategory.DELIVERY,
                    new BigDecimal("0.6"), null);
            WeeklyCommitEntity commit3 = makeCommit(
                    c3, planId, ChessPriority.ROOK, CommitCategory.DELIVERY,
                    new BigDecimal("0.9"), null);

            stubComputeRepositories(
                    List.of(plan),
                    List.of(commit1, commit2, commit3),
                    List.of(
                            makeActual(c1, CompletionStatus.DONE),
                            makeActual(c2, CompletionStatus.DONE),
                            makeActual(c3, CompletionStatus.PARTIALLY)
                    )
            );
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            UserModelSnapshotEntity snapshot = service.computeSnapshot(ORG_ID, USER_ID, 4);

            Map<String, Object> model = objectMapper.readValue(
                    snapshot.getModelJson(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> performance = (Map<String, Object>) model.get("performanceProfile");

            // (0.8 + 0.6) / 3 reconciled commits
            double expectedAccuracy = (0.8 + 0.6) / 3;
            assertEquals(expectedAccuracy,
                    ((Number) performance.get("estimationAccuracy")).doubleValue(), 0.0001);
        }

        /**
         * 4 reconciled commits, 3 DONE and 1 NOT_DONE.
         * completionReliability = 3 / 4 = 0.75
         */
        @Test
        void computesCorrectCompletionReliability() throws Exception {
            UUID planId = UUID.randomUUID();
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            UUID c3 = UUID.randomUUID();
            UUID c4 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(planId, PlanState.RECONCILED);
            WeeklyCommitEntity commit1 = makeCommit(
                    c1, planId, ChessPriority.KING, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit2 = makeCommit(
                    c2, planId, ChessPriority.QUEEN, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit3 = makeCommit(
                    c3, planId, ChessPriority.ROOK, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit4 = makeCommit(
                    c4, planId, ChessPriority.BISHOP, CommitCategory.DELIVERY, null, null);

            stubComputeRepositories(
                    List.of(plan),
                    List.of(commit1, commit2, commit3, commit4),
                    List.of(
                            makeActual(c1, CompletionStatus.DONE),
                            makeActual(c2, CompletionStatus.DONE),
                            makeActual(c3, CompletionStatus.DONE),
                            makeActual(c4, CompletionStatus.NOT_DONE)
                    )
            );
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            UserModelSnapshotEntity snapshot = service.computeSnapshot(ORG_ID, USER_ID, 4);

            Map<String, Object> model = objectMapper.readValue(
                    snapshot.getModelJson(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> performance = (Map<String, Object>) model.get("performanceProfile");

            assertEquals(0.75,
                    ((Number) performance.get("completionReliability")).doubleValue(), 0.0001);
        }

        /**
         * User with no plan history: planRepository returns an empty list.
         * The snapshot should record weeksAnalyzed = 0.
         */
        @Test
        void handlesUserWithNoHistory() {
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());
            when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserModelSnapshotEntity snapshot = service.computeSnapshot(ORG_ID, USER_ID, 4);

            assertEquals(0, snapshot.getWeeksAnalyzed());
        }

        /**
         * 2 DELIVERY commits (1 DONE, 1 NOT_DONE) and 2 OPERATIONS commits (both DONE).
         * Expected: DELIVERY rate = 0.5, OPERATIONS rate = 1.0.
         */
        @Test
        void computesCategoryCompletionRates() throws Exception {
            UUID planId = UUID.randomUUID();
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            UUID c3 = UUID.randomUUID();
            UUID c4 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(planId, PlanState.RECONCILED);
            WeeklyCommitEntity commit1 = makeCommit(
                    c1, planId, ChessPriority.KING, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit2 = makeCommit(
                    c2, planId, ChessPriority.QUEEN, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit3 = makeCommit(
                    c3, planId, ChessPriority.ROOK, CommitCategory.OPERATIONS, null, null);
            WeeklyCommitEntity commit4 = makeCommit(
                    c4, planId, ChessPriority.BISHOP, CommitCategory.OPERATIONS, null, null);

            stubComputeRepositories(
                    List.of(plan),
                    List.of(commit1, commit2, commit3, commit4),
                    List.of(
                            makeActual(c1, CompletionStatus.DONE),
                            makeActual(c2, CompletionStatus.NOT_DONE),
                            makeActual(c3, CompletionStatus.DONE),
                            makeActual(c4, CompletionStatus.DONE)
                    )
            );
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            UserModelSnapshotEntity snapshot = service.computeSnapshot(ORG_ID, USER_ID, 4);

            Map<String, Object> model = objectMapper.readValue(
                    snapshot.getModelJson(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> performance = (Map<String, Object>) model.get("performanceProfile");
            @SuppressWarnings("unchecked")
            Map<String, Object> categoryRates =
                    (Map<String, Object>) performance.get("categoryCompletionRates");

            assertEquals(0.5, ((Number) categoryRates.get("DELIVERY")).doubleValue(), 0.0001);
            assertEquals(1.0, ((Number) categoryRates.get("OPERATIONS")).doubleValue(), 0.0001);
        }

        /**
         * Commits across KING (1 DONE), QUEEN (1 DONE + 1 NOT_DONE), ROOK (1 DONE + 1 NOT_DONE).
         * Expected: KING = 1.0, QUEEN = 0.5, ROOK = 0.5.
         */
        @Test
        void computesPriorityCompletionRates() throws Exception {
            UUID planId = UUID.randomUUID();
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            UUID c3 = UUID.randomUUID();
            UUID c4 = UUID.randomUUID();
            UUID c5 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(planId, PlanState.RECONCILED);
            WeeklyCommitEntity commit1 = makeCommit(
                    c1, planId, ChessPriority.KING, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit2 = makeCommit(
                    c2, planId, ChessPriority.QUEEN, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit3 = makeCommit(
                    c3, planId, ChessPriority.QUEEN, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit4 = makeCommit(
                    c4, planId, ChessPriority.ROOK, CommitCategory.DELIVERY, null, null);
            WeeklyCommitEntity commit5 = makeCommit(
                    c5, planId, ChessPriority.ROOK, CommitCategory.DELIVERY, null, null);

            stubComputeRepositories(
                    List.of(plan),
                    List.of(commit1, commit2, commit3, commit4, commit5),
                    List.of(
                            makeActual(c1, CompletionStatus.DONE),
                            makeActual(c2, CompletionStatus.DONE),
                            makeActual(c3, CompletionStatus.NOT_DONE),
                            makeActual(c4, CompletionStatus.DONE),
                            makeActual(c5, CompletionStatus.NOT_DONE)
                    )
            );
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            UserModelSnapshotEntity snapshot = service.computeSnapshot(ORG_ID, USER_ID, 4);

            Map<String, Object> model = objectMapper.readValue(
                    snapshot.getModelJson(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> performance = (Map<String, Object>) model.get("performanceProfile");
            @SuppressWarnings("unchecked")
            Map<String, Object> priorityRates =
                    (Map<String, Object>) performance.get("priorityCompletionRates");

            assertEquals(1.0, ((Number) priorityRates.get("KING")).doubleValue(), 0.0001);
            assertEquals(0.5, ((Number) priorityRates.get("QUEEN")).doubleValue(), 0.0001);
            assertEquals(0.5, ((Number) priorityRates.get("ROOK")).doubleValue(), 0.0001);
        }

        /**
         * When a snapshot already exists for the user, the service must update
         * the existing entity in place rather than creating a new one.
         */
        @Test
        void upsertsExistingSnapshot() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(planId, PlanState.RECONCILED);

            stubComputeRepositories(List.of(plan), List.of(), List.of());

            UserModelSnapshotEntity existing = new UserModelSnapshotEntity(
                    ORG_ID, USER_ID, 3, "{}");
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(existing));

            service.computeSnapshot(ORG_ID, USER_ID, 4);

            ArgumentCaptor<UserModelSnapshotEntity> captor =
                    ArgumentCaptor.forClass(UserModelSnapshotEntity.class);
            verify(snapshotRepository).save(captor.capture());

            UserModelSnapshotEntity saved = captor.getValue();
            assertSame(existing, saved,
                    "save() must be called with the pre-existing entity, not a newly created one");
            assertEquals(1, saved.getWeeksAnalyzed(),
                    "weeksAnalyzed should be updated to reflect the number of plans returned");
        }
    }

    // ── @Nested GetSnapshot ───────────────────────────────────────────────────

    @Nested
    class GetSnapshot {

        /**
         * When a snapshot entity exists, getSnapshot maps its modelJson to a
         * fully populated {@link UserProfileResponse}.
         */
        @Test
        void returnsProfileWhenSnapshotExists() {
            UserModelSnapshotEntity entity = new UserModelSnapshotEntity(
                    ORG_ID, USER_ID, 4,
                    """
                    {
                      "performanceProfile": {
                        "estimationAccuracy": 0.6,
                        "completionReliability": 0.8,
                        "avgCommitsPerWeek": 3.0,
                        "avgCarryForwardPerWeek": 0.5,
                        "topCategories": ["DELIVERY"],
                        "categoryCompletionRates": {"DELIVERY": 0.8},
                        "priorityCompletionRates": {"KING": 1.0}
                      },
                      "preferences": {
                        "typicalPriorityPattern": "2K-1Q",
                        "recurringCommitTitles": [],
                        "avgCheckInsPerWeek": 2.0,
                        "preferredUpdateDays": ["TUESDAY"]
                      },
                      "trends": {
                        "strategicAlignmentTrend": "IMPROVING",
                        "completionTrend": "STABLE",
                        "carryForwardTrend": "WORSENING"
                      }
                    }
                    """
            );
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(entity));

            Optional<UserProfileResponse> result = service.getSnapshot(ORG_ID, USER_ID);

            assertTrue(result.isPresent());
            UserProfileResponse profile = result.get();
            assertEquals(USER_ID.toString(), profile.userId());
            assertEquals(4, profile.weeksAnalyzed());
            assertEquals(0.6, profile.performanceProfile().estimationAccuracy(), 0.0001);
            assertEquals(0.8, profile.performanceProfile().completionReliability(), 0.0001);
            assertEquals("2K-1Q", profile.preferences().typicalPriorityPattern());
            assertEquals("IMPROVING", profile.trends().strategicAlignmentTrend());
            assertEquals("STABLE", profile.trends().completionTrend());
            assertEquals("WORSENING", profile.trends().carryForwardTrend());
        }

        /**
         * When no snapshot exists for the user, getSnapshot returns an empty Optional.
         */
        @Test
        void returnsEmptyProfileWhenNoSnapshot() {
            when(snapshotRepository.findByOrgIdAndUserId(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            Optional<UserProfileResponse> result = service.getSnapshot(ORG_ID, USER_ID);

            assertTrue(result.isEmpty());
        }
    }
}
