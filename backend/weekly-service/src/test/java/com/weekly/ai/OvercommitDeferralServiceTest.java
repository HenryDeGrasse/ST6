package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.CapacityProfileProvider.CapacityProfileSnapshot;
import com.weekly.shared.DeferralPlanDataProvider;
import com.weekly.shared.DeferralPlanDataProvider.PlanAssignmentSummary;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OvercommitDeferralService}.
 */
class OvercommitDeferralServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);

    private DeferralPlanDataProvider planDataProvider;
    private IssueRepository issueRepository;
    private CapacityProfileProvider capacityProfileProvider;
    private UrgencyDataProvider urgencyDataProvider;
    private OvercommitDeferralService service;

    @BeforeEach
    void setUp() {
        planDataProvider = mock(DeferralPlanDataProvider.class);
        issueRepository = mock(IssueRepository.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        service = new OvercommitDeferralService(
                planDataProvider, issueRepository, capacityProfileProvider, urgencyDataProvider);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private static CapacityProfileSnapshot profile(double cap) {
        return new CapacityProfileSnapshot(
                USER_ID, 4, BigDecimal.ONE, BigDecimal.valueOf(cap), "HIGH", null);
    }

    private static PlanAssignmentSummary summary(UUID assignmentId, UUID issueId,
            String chessPriorityOverride) {
        return new PlanAssignmentSummary(assignmentId, issueId, chessPriorityOverride);
    }

    private static IssueEntity issue(UUID issueId, String chessName,
            BigDecimal estimatedHours) {
        IssueEntity issue = new IssueEntity(
                issueId, ORG_ID, UUID.randomUUID(),
                "TEST-" + issueId.toString().substring(0, 4),
                1, "Test issue", USER_ID);
        // Chess priority set via string-based approach
        if (chessName != null) {
            try {
                com.weekly.plan.domain.ChessPriority cp =
                        com.weekly.plan.domain.ChessPriority.valueOf(chessName);
                issue.setChessPriority(cp);
            } catch (IllegalArgumentException ignored) {
                // leave null
            }
        }
        issue.setEstimatedHours(estimatedHours);
        return issue;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    class WhenCapacityProfileMissing {
        @Test
        void returnsUnavailable() {
            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.empty());

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("unavailable", result.status());
            assertTrue(result.suggestions().isEmpty());
        }
    }

    @Nested
    class WhenNoPlanExists {
        @Test
        void returnsNoOvercommit() {
            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(40.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of());

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("no_overcommit", result.status());
            assertTrue(result.suggestions().isEmpty());
        }
    }

    @Nested
    class WhenPlanFitsWithinCap {
        @Test
        void returnsNoOvercommit() {
            UUID issueId = UUID.randomUUID();
            UUID assignmentId = UUID.randomUUID();

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(40.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(summary(assignmentId, issueId, null)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId))
                    .thenReturn(Optional.of(issue(issueId, "ROOK", BigDecimal.valueOf(20.0))));

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("no_overcommit", result.status());
            assertTrue(result.suggestions().isEmpty());
        }
    }

    @Nested
    class WhenOvercommitted {

        @Test
        void suggestsLowestPriorityItemFirst() {
            UUID kingIssueId = UUID.randomUUID();
            UUID rookIssueId = UUID.randomUUID();
            UUID pawnIssueId = UUID.randomUUID();
            UUID kingAssignmentId = UUID.randomUUID();
            UUID rookAssignmentId = UUID.randomUUID();
            UUID pawnAssignmentId = UUID.randomUUID();

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(40.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(
                            summary(kingAssignmentId, kingIssueId, null),
                            summary(rookAssignmentId, rookIssueId, null),
                            summary(pawnAssignmentId, pawnIssueId, null)
                    ));
            when(issueRepository.findByOrgIdAndId(ORG_ID, kingIssueId))
                    .thenReturn(Optional.of(issue(kingIssueId, "KING", BigDecimal.valueOf(20.0))));
            when(issueRepository.findByOrgIdAndId(ORG_ID, rookIssueId))
                    .thenReturn(Optional.of(issue(rookIssueId, "ROOK", BigDecimal.valueOf(10.0))));
            when(issueRepository.findByOrgIdAndId(ORG_ID, pawnIssueId))
                    .thenReturn(Optional.of(issue(pawnIssueId, "PAWN", BigDecimal.valueOf(20.0))));

            when(urgencyDataProvider.getOutcomeUrgency(any(), any())).thenReturn(null);

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // Total = 50h, cap = 40h — needs to free 10h
            // KING is never deferred; PAWN (20h) deferred first (frees 20h > 10h needed)
            assertEquals(1, result.suggestions().size());
            assertEquals(pawnIssueId, result.suggestions().get(0).issueId());
        }

        @Test
        void doesNotSuggestKingPriorityAssignments() {
            UUID kingIssueId = UUID.randomUUID();
            UUID assignmentId = UUID.randomUUID();

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(5.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(summary(assignmentId, kingIssueId, null)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, kingIssueId))
                    .thenReturn(Optional.of(issue(kingIssueId, "KING", BigDecimal.valueOf(40.0))));

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // KING is never deferred even though plan is overcommitted
            assertTrue(result.suggestions().isEmpty());
        }

        @Test
        void doesNotSuggestCriticalUrgencyAssignments() {
            UUID issueId = UUID.randomUUID();
            UUID outcomeId = UUID.randomUUID();
            UUID assignmentId = UUID.randomUUID();

            IssueEntity criticalIssue = issue(issueId, "PAWN", BigDecimal.valueOf(40.0));
            criticalIssue.setOutcomeId(outcomeId);

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(5.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(summary(assignmentId, issueId, null)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId))
                    .thenReturn(Optional.of(criticalIssue));
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, outcomeId))
                    .thenReturn(new UrgencyInfo(outcomeId, "Critical Outcome", null, null, null, "CRITICAL", 7L));

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // CRITICAL urgency is never deferred
            assertTrue(result.suggestions().isEmpty());
        }

        @Test
        void prefersLowerUrgencyOverHigherWithSamePriority() {
            UUID atRiskIssueId = UUID.randomUUID();
            UUID onTrackIssueId = UUID.randomUUID();
            UUID atRiskOutcomeId = UUID.randomUUID();
            UUID atRiskAssignmentId = UUID.randomUUID();
            UUID onTrackAssignmentId = UUID.randomUUID();

            IssueEntity atRiskIssue = issue(atRiskIssueId, "ROOK", BigDecimal.valueOf(15.0));
            atRiskIssue.setOutcomeId(atRiskOutcomeId);

            IssueEntity onTrackIssue = issue(onTrackIssueId, "ROOK", BigDecimal.valueOf(15.0));
            // no outcome => ON_TRACK

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(20.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(
                            summary(atRiskAssignmentId, atRiskIssueId, null),
                            summary(onTrackAssignmentId, onTrackIssueId, null)
                    ));
            when(issueRepository.findByOrgIdAndId(ORG_ID, atRiskIssueId))
                    .thenReturn(Optional.of(atRiskIssue));
            when(issueRepository.findByOrgIdAndId(ORG_ID, onTrackIssueId))
                    .thenReturn(Optional.of(onTrackIssue));
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, atRiskOutcomeId))
                    .thenReturn(new UrgencyInfo(atRiskOutcomeId, "At Risk Outcome", null, null, null, "AT_RISK", 14L));

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // Total = 30h, cap = 20h — needs to free 10h.
            // Both are ROOK priority. ON_TRACK has lower urgency → deferred first.
            assertEquals(1, result.suggestions().size());
            assertEquals(onTrackIssueId, result.suggestions().get(0).issueId());
        }

        @Test
        void multipleDeferralsUntilFitsWithinCap() {
            List<UUID> issueIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            List<UUID> assignmentIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(8.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(
                            summary(assignmentIds.get(0), issueIds.get(0), null),
                            summary(assignmentIds.get(1), issueIds.get(1), null),
                            summary(assignmentIds.get(2), issueIds.get(2), null)
                    ));
            for (int i = 0; i < 3; i++) {
                UUID issueId = issueIds.get(i);
                when(issueRepository.findByOrgIdAndId(ORG_ID, issueId))
                        .thenReturn(Optional.of(issue(issueId, "PAWN", BigDecimal.valueOf(5.0))));
            }
            when(urgencyDataProvider.getOutcomeUrgency(any(), any())).thenReturn(null);

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // Total = 15h, cap = 8h. Need to defer at least 2 (to bring to 5h ≤ 8h)
            assertEquals(2, result.suggestions().size());
        }

        @Test
        void chessPriorityOverrideOnAssignmentTakesPrecedence() {
            UUID issueId = UUID.randomUUID();
            UUID assignmentId = UUID.randomUUID();

            // Issue has KING priority, but assignment overrides to PAWN
            IssueEntity issueWithKing = issue(issueId, "KING", BigDecimal.valueOf(20.0));

            when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID))
                    .thenReturn(Optional.of(profile(5.0)));
            when(planDataProvider.getCurrentPlanAssignments(ORG_ID, USER_ID, WEEK_START))
                    .thenReturn(List.of(summary(assignmentId, issueId, "PAWN")));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId))
                    .thenReturn(Optional.of(issueWithKing));
            when(urgencyDataProvider.getOutcomeUrgency(any(), any())).thenReturn(null);

            OvercommitDeferralService.DeferralResult result =
                    service.suggestDeferrals(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            // Override is PAWN — deferrable despite issue being KING
            assertEquals(1, result.suggestions().size());
            assertEquals(issueId, result.suggestions().get(0).issueId());
        }
    }

    @Nested
    class ChessDeferralOrdinalTests {

        @Test
        void ordinalValuesOrderedCorrectly() {
            // PAWN has highest ordinal (best to defer), KING has lowest (never defer)
            assertTrue(OvercommitDeferralService.chessDeferralOrdinal("PAWN")
                    > OvercommitDeferralService.chessDeferralOrdinal("KNIGHT"));
            assertTrue(OvercommitDeferralService.chessDeferralOrdinal("KNIGHT")
                    > OvercommitDeferralService.chessDeferralOrdinal("BISHOP"));
            assertTrue(OvercommitDeferralService.chessDeferralOrdinal("BISHOP")
                    > OvercommitDeferralService.chessDeferralOrdinal("ROOK"));
            assertTrue(OvercommitDeferralService.chessDeferralOrdinal("ROOK")
                    > OvercommitDeferralService.chessDeferralOrdinal("QUEEN"));
            assertTrue(OvercommitDeferralService.chessDeferralOrdinal("QUEEN")
                    > OvercommitDeferralService.chessDeferralOrdinal("KING"));
        }

        @Test
        void nullPriorityTreatedAsLowest() {
            assertEquals(OvercommitDeferralService.CHESS_DEFERRAL_PAWN,
                    OvercommitDeferralService.chessDeferralOrdinal(null));
        }

        @Test
        void caseInsensitive() {
            assertEquals(OvercommitDeferralService.CHESS_DEFERRAL_ROOK,
                    OvercommitDeferralService.chessDeferralOrdinal("rook"));
        }
    }
}
