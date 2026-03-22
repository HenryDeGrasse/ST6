package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.CapacityProfileProvider.CapacityProfileSnapshot;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BacklogRankingService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Formula correctness for all four factors</li>
 *   <li>Default values when data is unavailable</li>
 *   <li>Dependency bonus applied only to unblocking issues</li>
 *   <li>Rank assignment (1 = highest score)</li>
 *   <li>Persistence — {@code saveAll} called with updated ranks</li>
 * </ul>
 */
class BacklogRankingServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private IssueRepository issueRepository;
    private UrgencyDataProvider urgencyDataProvider;
    private CapacityProfileProvider capacityProfileProvider;
    private BacklogRankingService service;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        service = new BacklogRankingService(issueRepository, urgencyDataProvider,
                capacityProfileProvider);
    }

    // ── Helper builders ────────────────────────────────────────────────────────

    private IssueEntity buildIssue(UUID id) {
        return new IssueEntity(id, ORG_ID, TEAM_ID, "TST-1", 1, "Test issue", USER_ID);
    }

    private UrgencyInfo urgencyInfo(String band, long daysRemaining) {
        return new UrgencyInfo(UUID.randomUUID(), "Outcome", null,
                BigDecimal.valueOf(50), BigDecimal.valueOf(50), band, daysRemaining);
    }

    private CapacityProfileSnapshot capacityProfile(double cap) {
        return new CapacityProfileSnapshot(USER_ID, 4, BigDecimal.ONE,
                BigDecimal.valueOf(cap), "HIGH", "2024-01-01T00:00:00Z");
    }

    // ── Empty team ─────────────────────────────────────────────────────────────

    @Test
    void returnsEmptyListWhenNoOpenIssues() {
        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of());

        List<BacklogRankingService.RankedIssue> result =
                service.rankTeamBacklog(ORG_ID, TEAM_ID);

        assertTrue(result.isEmpty());
    }

    // ── Urgency weight ─────────────────────────────────────────────────────────

    @Nested
    class UrgencyWeightTests {

        @Test
        void criticalYieldsWeight4() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("CRITICAL", Long.MIN_VALUE));

            double weight = service.computeUrgencyWeight(issue, ORG_ID);
            assertEquals(4.0, weight);
        }

        @Test
        void atRiskYieldsWeight3() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("AT_RISK", Long.MIN_VALUE));

            assertEquals(3.0, service.computeUrgencyWeight(issue, ORG_ID));
        }

        @Test
        void needsAttentionYieldsWeight2() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("NEEDS_ATTENTION", Long.MIN_VALUE));

            assertEquals(2.0, service.computeUrgencyWeight(issue, ORG_ID));
        }

        @Test
        void onTrackYieldsWeight1() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("ON_TRACK", Long.MIN_VALUE));

            assertEquals(1.0, service.computeUrgencyWeight(issue, ORG_ID));
        }

        @Test
        void defaultsTo1WhenNoOutcomeId() {
            IssueEntity issue = buildIssue(UUID.randomUUID()); // no outcomeId
            assertEquals(1.0, service.computeUrgencyWeight(issue, ORG_ID));
        }

        @Test
        void defaultsTo1WhenUrgencyInfoNull() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId)).thenReturn(null);

            assertEquals(1.0, service.computeUrgencyWeight(issue, ORG_ID));
        }
    }

    // ── Time pressure ──────────────────────────────────────────────────────────

    @Nested
    class TimePressureTests {

        @Test
        void highPressureWhenOneWeekRemaining() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            // 7 days = 1 week → max(1, 5-1)/4 = 4/4 = 1.0
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("ON_TRACK", 7L));

            assertEquals(1.0, service.computeTimePressure(issue, ORG_ID), 0.001);
        }

        @Test
        void maximumPressureWhenDueTodayOrOverdue() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            // 0 days = 0 weeks → max(1, 5-0)/4 = 5/4 = 1.25
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("CRITICAL", 0L));

            assertEquals(1.25, service.computeTimePressure(issue, ORG_ID), 0.001);
        }

        @Test
        void overduePressureExceedsOnTimeDeadline() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            // -14 days = -2 weeks → max(1, 5-(-2))/4 = 7/4 = 1.75
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("CRITICAL", -14L));

            assertEquals(1.75, service.computeTimePressure(issue, ORG_ID), 0.001);
        }

        @Test
        void minimumPressureWhenFarFromDeadline() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            // 70 days = 10 weeks → max(1, 5-10)/4 = 1/4 = 0.25
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("ON_TRACK", 70L));

            assertEquals(0.25, service.computeTimePressure(issue, ORG_ID), 0.001);
        }

        @Test
        void defaultsTo1WhenNoTargetDate() {
            UUID outcomeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setOutcomeId(outcomeId);
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(urgencyInfo("NO_TARGET", Long.MIN_VALUE));

            assertEquals(1.0, service.computeTimePressure(issue, ORG_ID));
        }

        @Test
        void defaultsTo1WhenNoOutcomeId() {
            IssueEntity issue = buildIssue(UUID.randomUUID());
            assertEquals(1.0, service.computeTimePressure(issue, ORG_ID));
        }
    }

    // ── Effort fit ─────────────────────────────────────────────────────────────

    @Nested
    class EffortFitTests {

        @Test
        void returnsOkWhenIssueHoursUnderCapacity() {
            UUID assigneeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setAssigneeUserId(assigneeId);
            issue.setEstimatedHours(BigDecimal.valueOf(10));
            when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                    .thenReturn(Optional.of(capacityProfile(20)));

            assertEquals(BacklogRankingService.EFFORT_FIT_OK,
                    service.computeEffortFit(issue, ORG_ID));
        }

        @Test
        void returnsOkWhenIssueHoursEqualCapacity() {
            UUID assigneeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setAssigneeUserId(assigneeId);
            issue.setEstimatedHours(BigDecimal.valueOf(20));
            when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                    .thenReturn(Optional.of(capacityProfile(20)));

            assertEquals(BacklogRankingService.EFFORT_FIT_OK,
                    service.computeEffortFit(issue, ORG_ID));
        }

        @Test
        void returnsOverFitWhenIssueHoursExceedCapacity() {
            UUID assigneeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setAssigneeUserId(assigneeId);
            issue.setEstimatedHours(BigDecimal.valueOf(30));
            when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                    .thenReturn(Optional.of(capacityProfile(20)));

            assertEquals(BacklogRankingService.EFFORT_FIT_OVER,
                    service.computeEffortFit(issue, ORG_ID));
        }

        @Test
        void defaultsToOkWhenNoEstimatedHours() {
            IssueEntity issue = buildIssue(UUID.randomUUID()); // no estimatedHours
            assertEquals(BacklogRankingService.EFFORT_FIT_OK,
                    service.computeEffortFit(issue, ORG_ID));
        }

        @Test
        void defaultsToOkWhenNoAssignee() {
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setEstimatedHours(BigDecimal.valueOf(10));
            // no assignee
            assertEquals(BacklogRankingService.EFFORT_FIT_OK,
                    service.computeEffortFit(issue, ORG_ID));
        }

        @Test
        void defaultsToOkWhenNoCapacityProfile() {
            UUID assigneeId = UUID.randomUUID();
            IssueEntity issue = buildIssue(UUID.randomUUID());
            issue.setAssigneeUserId(assigneeId);
            issue.setEstimatedHours(BigDecimal.valueOf(10));
            when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                    .thenReturn(Optional.empty());

            assertEquals(BacklogRankingService.EFFORT_FIT_OK,
                    service.computeEffortFit(issue, ORG_ID));
        }
    }

    // ── Dependency bonus ───────────────────────────────────────────────────────

    @Nested
    class DependencyBonusTests {

        @Test
        void issueUnblocksOtherGetsHigherScore() {
            // Issue A unblocks Issue B (B.blockedByIssueId = A.id)
            UUID issueAId = UUID.randomUUID();
            UUID issueBId = UUID.randomUUID();

            IssueEntity issueA = buildIssue(issueAId);
            IssueEntity issueB = buildIssue(issueBId);
            issueB.setBlockedByIssueId(issueAId); // B is blocked by A → A unblocks B

            when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                    .thenReturn(List.of(issueA, issueB));
            when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<BacklogRankingService.RankedIssue> result =
                    service.rankTeamBacklog(ORG_ID, TEAM_ID);

            // issueA should be rank 1 (higher score due to dependency bonus 1.5)
            // Both have no urgency info (weight=1), no target (pressure=1.0), no estimate (fit=1.0)
            // issueA: 1 * 1 * 1 * 1.5 = 1.5
            // issueB: 1 * 1 * 1 * 1.0 = 1.0

            BacklogRankingService.RankedIssue ranked1 = result.get(0);
            assertEquals(issueAId, ranked1.issueId());
            assertEquals(1.5, ranked1.score(), 0.001);
        }

        @Test
        void issueNotUnblockingOthersGetsNeutralBonus() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = buildIssue(issueId);
            // no other issue references this as a blocker

            when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                    .thenReturn(List.of(issue));
            when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<BacklogRankingService.RankedIssue> result =
                    service.rankTeamBacklog(ORG_ID, TEAM_ID);

            assertEquals(1, result.size());
            assertEquals(1.0, result.get(0).score(), 0.001); // 1*1*1*1 = 1.0
        }
    }

    // ── Full formula integration ───────────────────────────────────────────────

    @Test
    void fullFormulaMaxScenario() {
        // CRITICAL urgency (4), 7 days remaining (1 week → pressure=1.0),
        // fits capacity (1.0), unblocks another issue (1.5)
        // Expected score = 4 * 1.0 * 1.0 * 1.5 = 6.0
        UUID outcomeId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID issueAId = UUID.randomUUID();
        UUID issueBId = UUID.randomUUID();

        IssueEntity issueA = buildIssue(issueAId);
        issueA.setOutcomeId(outcomeId);
        issueA.setAssigneeUserId(assigneeId);
        issueA.setEstimatedHours(BigDecimal.valueOf(10));

        IssueEntity issueB = buildIssue(issueBId);
        issueB.setBlockedByIssueId(issueAId); // A unblocks B

        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                .thenReturn(urgencyInfo("CRITICAL", 7L)); // 1 week
        when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                .thenReturn(Optional.of(capacityProfile(20))); // 10h <= 20h → fit

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(issueA, issueB));
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result =
                service.rankTeamBacklog(ORG_ID, TEAM_ID);

        BacklogRankingService.RankedIssue topRanked = result.get(0);
        assertEquals(issueAId, topRanked.issueId());
        assertEquals(6.0, topRanked.score(), 0.001);
    }

    @Test
    void fullFormulaMinScenario() {
        // ON_TRACK (1), far target (70 days → pressure=0.25),
        // over capacity (0.5), does not unblock (1.0)
        // Expected score = 1 * 0.25 * 0.5 * 1.0 = 0.125
        UUID outcomeId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        IssueEntity issue = buildIssue(issueId);
        issue.setOutcomeId(outcomeId);
        issue.setAssigneeUserId(assigneeId);
        issue.setEstimatedHours(BigDecimal.valueOf(30)); // over cap

        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                .thenReturn(urgencyInfo("ON_TRACK", 70L)); // 10 weeks
        when(capacityProfileProvider.getLatestProfile(ORG_ID, assigneeId))
                .thenReturn(Optional.of(capacityProfile(20))); // 30h > 20h → over

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(issue));
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result =
                service.rankTeamBacklog(ORG_ID, TEAM_ID);

        assertEquals(1, result.size());
        assertEquals(0.125, result.get(0).score(), 0.001);
    }

    // ── Rank assignment ────────────────────────────────────────────────────────

    @Test
    void ranksAssignedInDescendingScoreOrder() {
        UUID outcomeIdA = UUID.randomUUID();
        UUID outcomeIdB = UUID.randomUUID();
        UUID issueAId = UUID.randomUUID();
        UUID issueBId = UUID.randomUUID();
        UUID issueCId = UUID.randomUUID();

        IssueEntity issueA = buildIssue(issueAId);
        issueA.setOutcomeId(outcomeIdA); // CRITICAL

        IssueEntity issueB = buildIssue(issueBId);
        issueB.setOutcomeId(outcomeIdB); // AT_RISK

        IssueEntity issueC = buildIssue(issueCId); // no outcome → weight 1

        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeIdA))
                .thenReturn(urgencyInfo("CRITICAL", Long.MIN_VALUE));
        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeIdB))
                .thenReturn(urgencyInfo("AT_RISK", Long.MIN_VALUE));

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(issueC, issueA, issueB)); // intentionally unordered
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result =
                service.rankTeamBacklog(ORG_ID, TEAM_ID);

        assertEquals(3, result.size());
        // Rank 1 → CRITICAL (score 4), Rank 2 → AT_RISK (score 3), Rank 3 → ON_TRACK (score 1)
        assertEquals(issueAId, result.get(0).issueId()); // rank 1
        assertEquals(issueBId, result.get(1).issueId()); // rank 2
        assertEquals(issueCId, result.get(2).issueId()); // rank 3
    }

    @Test
    void ranksPersistedOnIssueEntities() {
        UUID issueAId = UUID.randomUUID();
        UUID issueBId = UUID.randomUUID();

        IssueEntity issueA = buildIssue(issueAId);
        UUID outcomeId = UUID.randomUUID();
        issueA.setOutcomeId(outcomeId); // CRITICAL → higher score → rank 1

        IssueEntity issueB = buildIssue(issueBId); // ON_TRACK → rank 2

        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                .thenReturn(urgencyInfo("CRITICAL", Long.MIN_VALUE));

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(issueA, issueB));
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result = service.rankTeamBacklog(ORG_ID, TEAM_ID);

        assertEquals(1, result.get(0).rank());
        assertEquals(2, result.get(1).rank());
        assertEquals(Integer.valueOf(1), issueA.getAiRecommendedRank());
        assertEquals(Integer.valueOf(2), issueB.getAiRecommendedRank());
        assertNotNull(issueA.getAiRankRationale());
        assertNotNull(issueB.getAiRankRationale());
        verify(issueRepository).saveAll(any());
    }

    @Test
    void tieScoresAreOrderedDeterministicallyByIssueId() {
        UUID laterId = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        UUID earlierId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        IssueEntity laterIssue = buildIssue(laterId);
        IssueEntity earlierIssue = buildIssue(earlierId);

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(laterIssue, earlierIssue));
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result = service.rankTeamBacklog(ORG_ID, TEAM_ID);

        assertEquals(earlierId, result.get(0).issueId());
        assertEquals(1, result.get(0).rank());
        assertEquals(laterId, result.get(1).issueId());
        assertEquals(2, result.get(1).rank());
    }

    // ── Rationale ─────────────────────────────────────────────────────────────

    @Test
    void rationaleContainsScoreComponents() {
        UUID issueId = UUID.randomUUID();
        IssueEntity issue = buildIssue(issueId);

        when(issueRepository.findAllByTeamIdAndStatusIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(issue));
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BacklogRankingService.RankedIssue> result =
                service.rankTeamBacklog(ORG_ID, TEAM_ID);

        String rationale = result.get(0).rationale();
        assertNotNull(rationale);
        assertTrue(rationale.contains("urgency="));
        assertTrue(rationale.contains("time_pressure="));
        assertTrue(rationale.contains("effort_fit="));
        assertTrue(rationale.contains("dependency_bonus="));
        assertTrue(rationale.contains("score="));
    }
}
