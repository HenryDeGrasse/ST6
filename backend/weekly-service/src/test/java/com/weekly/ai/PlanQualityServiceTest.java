package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.CapacityQualityProvider;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import com.weekly.shared.PlanQualityDataProvider;
import com.weekly.shared.PlanQualityDataProvider.CommitQualitySummary;
import com.weekly.shared.PlanQualityDataProvider.PlanQualityContext;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.TeamRcdoUsageProvider;
import com.weekly.shared.TeamRcdoUsageProvider.OutcomeUsage;
import com.weekly.shared.TeamRcdoUsageProvider.TeamRcdoUsageResult;
import com.weekly.shared.UrgencyDataProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultPlanQualityService}.
 *
 * <p>Each nested class covers one of the service's data-driven checks:
 * <ol>
 *   <li>Coverage gaps</li>
 *   <li>Category imbalance</li>
 *   <li>Chess distribution balance</li>
 *   <li>RCDO alignment</li>
 *   <li>Urgency and capacity integration</li>
 * </ol>
 */
class PlanQualityServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(DayOfWeek.MONDAY);

    // Fixed RCDO identifiers
    private static final String RC1_ID = UUID.randomUUID().toString();
    private static final String RC2_ID = UUID.randomUUID().toString();
    private static final String RC3_ID = UUID.randomUUID().toString();
    private static final String OBJ1_ID = UUID.randomUUID().toString();
    private static final String OBJ2_ID = UUID.randomUUID().toString();
    private static final String OBJ3_ID = UUID.randomUUID().toString();
    private static final String OUT1_ID = UUID.randomUUID().toString();
    private static final String OUT2_ID = UUID.randomUUID().toString();
    private static final String OUT3_ID = UUID.randomUUID().toString();

    private PlanQualityDataProvider qualityDataProvider;
    private TeamRcdoUsageProvider teamRcdoUsageProvider;
    private RcdoClient rcdoClient;
    private UrgencyDataProvider urgencyDataProvider;
    private CapacityQualityProvider capacityQualityProvider;
    private DefaultPlanQualityService service;

    /** Minimal RCDO tree: three rally cries, one objective each, one outcome each. */
    private RcdoTree testTree;

    @BeforeEach
    void setUp() {
        qualityDataProvider = mock(PlanQualityDataProvider.class);
        teamRcdoUsageProvider = mock(TeamRcdoUsageProvider.class);
        rcdoClient = mock(RcdoClient.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        capacityQualityProvider = mock(CapacityQualityProvider.class);
        service = new DefaultPlanQualityService(
                qualityDataProvider, teamRcdoUsageProvider, rcdoClient,
                urgencyDataProvider, capacityQualityProvider);

        testTree = new RcdoTree(List.of(
                new RcdoTree.RallyCry(RC1_ID, "Win New Markets", List.of(
                        new RcdoTree.Objective(OBJ1_ID, "Expand Outreach", RC1_ID, List.of(
                                new RcdoTree.Outcome(OUT1_ID, "Reach 100 leads", OBJ1_ID)
                        ))
                )),
                new RcdoTree.RallyCry(RC2_ID, "Retain Customers", List.of(
                        new RcdoTree.Objective(OBJ2_ID, "Improve NPS", RC2_ID, List.of(
                                new RcdoTree.Outcome(OUT2_ID, "NPS +10", OBJ2_ID)
                        ))
                )),
                new RcdoTree.RallyCry(RC3_ID, "Reduce Churn", List.of(
                        new RcdoTree.Objective(OBJ3_ID, "Retention OKR", RC3_ID, List.of(
                                new RcdoTree.Outcome(OUT3_ID, "Churn <5%", OBJ3_ID)
                        ))
                ))
        ));

        // Default stubs
        when(rcdoClient.getTree(ORG_ID)).thenReturn(testTree);
        when(teamRcdoUsageProvider.getTeamRcdoUsage(eq(ORG_ID), any()))
                .thenReturn(new TeamRcdoUsageResult(List.of(), Set.of()));
        when(qualityDataProvider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID))
                .thenReturn(new PlanQualityContext(true, WEEK_START.toString(), List.of()));
        when(qualityDataProvider.getPreviousWeekQualityContext(eq(ORG_ID), eq(USER_ID), any()))
                .thenReturn(PlanQualityContext.empty());
        when(qualityDataProvider.getTeamStrategicAlignmentRate(eq(ORG_ID), any()))
                .thenReturn(0.0);
        when(urgencyDataProvider.getStrategicSlack(ORG_ID, USER_ID))
                .thenReturn(new SlackInfo("HIGH_SLACK", new BigDecimal("0.50"), 0, 0));
        when(capacityQualityProvider.getOvercommitmentWarning(ORG_ID, PLAN_ID, USER_ID))
                .thenReturn(java.util.Optional.empty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CommitQualitySummary strategicCommit(String outcomeId) {
        return new CommitQualitySummary("DELIVERY", "QUEEN", outcomeId, null);
    }

    private CommitQualitySummary nonStrategicCommit() {
        return new CommitQualitySummary("OPERATIONS", "ROOK", null, null);
    }

    private CommitQualitySummary commitWithCategory(String category) {
        return new CommitQualitySummary(category, "BISHOP", null, null);
    }

    private CommitQualitySummary commitWithPriority(String priority) {
        return new CommitQualitySummary("DELIVERY", priority, null, null);
    }

    private void stubPlanCommits(List<CommitQualitySummary> commits) {
        when(qualityDataProvider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID))
                .thenReturn(new PlanQualityContext(true, WEEK_START.toString(), commits));
    }

    // ── Plan not found ────────────────────────────────────────────────────────

    @Test
    void returnEmptyNudgesWhenPlanNotFound() {
        when(qualityDataProvider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID))
                .thenReturn(PlanQualityContext.empty());

        PlanQualityService.QualityCheckResult result =
                service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

        assertEquals("ok", result.status());
        assertTrue(result.nudges().isEmpty());
    }

    // ── Coverage gap check ────────────────────────────────────────────────────

    @Nested
    class CoverageGapCheck {

        @Test
        void noNudgeWhenFewerThanMinCommits() {
            stubPlanCommits(List.of(nonStrategicCommit()));
            when(teamRcdoUsageProvider.getTeamRcdoUsage(eq(ORG_ID), any()))
                    .thenReturn(new TeamRcdoUsageResult(
                            List.of(new OutcomeUsage(OUT1_ID, "Reach 100 leads", 5)),
                            Set.of()
                    ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "COVERAGE_GAP");
        }

        @Test
        void warningWhenUserCoversNoneOfTopRallyCries() {
            // Team is active on RC1 (top), but user's commits link to RC3
            stubPlanCommits(List.of(
                    strategicCommit(OUT3_ID),  // RC3
                    nonStrategicCommit()
            ));
            when(teamRcdoUsageProvider.getTeamRcdoUsage(eq(ORG_ID), any()))
                    .thenReturn(new TeamRcdoUsageResult(
                            List.of(
                                    new OutcomeUsage(OUT1_ID, "Reach 100 leads", 10),
                                    new OutcomeUsage(OUT2_ID, "NPS +10", 5)
                            ),
                            Set.of()
                    ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "COVERAGE_GAP");
            assertEquals("WARNING", nudge.severity());
            assertTrue(nudge.message().contains("Win New Markets"),
                    "Message should name the uncovered rally cry");
        }

        @Test
        void noNudgeWhenUserCoversTopRallyCry() {
            // User commits link to RC1 which is also the team's top
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),  // RC1
                    nonStrategicCommit()
            ));
            when(teamRcdoUsageProvider.getTeamRcdoUsage(eq(ORG_ID), any()))
                    .thenReturn(new TeamRcdoUsageResult(
                            List.of(new OutcomeUsage(OUT1_ID, "Reach 100 leads", 10)),
                            Set.of()
                    ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "COVERAGE_GAP");
        }

        @Test
        void usesSnapshotRallyCryIdWhenAvailable() {
            // Commit has a snapshot rally cry ID (plan is already locked)
            CommitQualitySummary lockedCommit = new CommitQualitySummary(
                    "DELIVERY", "QUEEN", OUT1_ID, RC1_ID);
            stubPlanCommits(List.of(lockedCommit, nonStrategicCommit()));
            when(teamRcdoUsageProvider.getTeamRcdoUsage(eq(ORG_ID), any()))
                    .thenReturn(new TeamRcdoUsageResult(
                            List.of(new OutcomeUsage(OUT1_ID, "Reach 100 leads", 10)),
                            Set.of()
                    ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "COVERAGE_GAP");
        }

        @Test
        void noNudgeWhenTeamUsageIsEmpty() {
            stubPlanCommits(List.of(nonStrategicCommit(), nonStrategicCommit()));
            // teamUsage is empty by default

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "COVERAGE_GAP");
        }
    }

    // ── Category imbalance check ──────────────────────────────────────────────

    @Nested
    class CategoryImbalanceCheck {

        @Test
        void noNudgeWhenBelowSkewThreshold() {
            stubPlanCommits(List.of(
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("OPERATIONS"),
                    commitWithCategory("PEOPLE")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "CATEGORY_IMBALANCE");
        }

        @Test
        void warningWhenNewDominantCategoryAboveThreshold() {
            stubPlanCommits(List.of(
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("OPERATIONS")
            ));
            // Previous week had no plan (default stub: empty)

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "CATEGORY_IMBALANCE");
            assertEquals("WARNING", nudge.severity());
            assertTrue(nudge.message().contains("DELIVERY"));
        }

        @Test
        void infoWhenSameDominantCategoryAsPreviousWeek() {
            stubPlanCommits(List.of(
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("OPERATIONS")
            ));
            // Previous week: same dominant category (DELIVERY)
            List<CommitQualitySummary> prevCommits = List.of(
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("DELIVERY"),
                    commitWithCategory("LEARNING")
            );
            when(qualityDataProvider.getPreviousWeekQualityContext(
                    eq(ORG_ID), eq(USER_ID), eq(WEEK_START)))
                    .thenReturn(new PlanQualityContext(true, WEEK_START.minusWeeks(1).toString(), prevCommits));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "CATEGORY_IMBALANCE");
            assertEquals("INFO", nudge.severity());
        }

        @Test
        void noNudgeWhenFewerThanMinCategorizedCommits() {
            stubPlanCommits(List.of(
                    commitWithCategory("DELIVERY")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "CATEGORY_IMBALANCE");
        }
    }

    // ── Chess distribution check ──────────────────────────────────────────────

    @Nested
    class ChessDistributionCheck {

        @Test
        void warningWhenNoKingAndSufficientCommits() {
            stubPlanCommits(List.of(
                    commitWithPriority("QUEEN"),
                    commitWithPriority("ROOK"),
                    commitWithPriority("PAWN")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "CHESS_NO_KING");
            assertEquals("WARNING", nudge.severity());
        }

        @Test
        void warningWhenPawnHeavy() {
            stubPlanCommits(List.of(
                    commitWithPriority("KING"),
                    commitWithPriority("PAWN"),
                    commitWithPriority("PAWN"),
                    commitWithPriority("PAWN")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "CHESS_PAWN_HEAVY");
            assertEquals("WARNING", nudge.severity());
        }

        @Test
        void positiveNudgeForBalancedDistribution() {
            stubPlanCommits(List.of(
                    commitWithPriority("KING"),
                    commitWithPriority("QUEEN"),
                    commitWithPriority("ROOK")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "CHESS_BALANCED");
            assertEquals("POSITIVE", nudge.severity());
        }

        @Test
        void noNudgeWhenFewerThanMinPrioritizedCommits() {
            stubPlanCommits(List.of(
                    commitWithPriority("PAWN")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "CHESS_NO_KING");
            assertNoNudgeOfType(result, "CHESS_PAWN_HEAVY");
            assertNoNudgeOfType(result, "CHESS_BALANCED");
        }

        @Test
        void bothNoKingAndPawnHeavyFireTogether() {
            stubPlanCommits(List.of(
                    commitWithPriority("PAWN"),
                    commitWithPriority("PAWN"),
                    commitWithPriority("PAWN")
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNudgeOfType(result, "CHESS_NO_KING");
            assertNudgeOfType(result, "CHESS_PAWN_HEAVY");
        }
    }

    // ── RCDO alignment check ──────────────────────────────────────────────────

    @Nested
    class RcdoAlignmentCheck {

        @Test
        void warningWhenZeroRcdoLinks() {
            stubPlanCommits(List.of(
                    nonStrategicCommit(),
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "ZERO_RCDO_ALIGNMENT");
            assertEquals("WARNING", nudge.severity());
        }

        @Test
        void warningWhenBelowTeamAverageByThreshold() {
            // User: 1/4 = 25%  Team: 60%  Gap: 35% > 20%
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),
                    nonStrategicCommit(),
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));
            when(qualityDataProvider.getTeamStrategicAlignmentRate(eq(ORG_ID), any()))
                    .thenReturn(0.60);

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "BELOW_TEAM_RCDO_ALIGNMENT");
            assertEquals("WARNING", nudge.severity());
            assertTrue(nudge.message().contains("25%"));
            assertTrue(nudge.message().contains("60%"));
        }

        @Test
        void positiveNudgeWhenHighAlignment() {
            // User: 4/4 = 100%
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),
                    strategicCommit(OUT2_ID),
                    strategicCommit(OUT1_ID),
                    strategicCommit(OUT2_ID)
            ));
            when(qualityDataProvider.getTeamStrategicAlignmentRate(eq(ORG_ID), any()))
                    .thenReturn(0.70);

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "HIGH_RCDO_ALIGNMENT");
            assertEquals("POSITIVE", nudge.severity());
        }

        @Test
        void noNudgeWhenUserRateIsWithinTeamThreshold() {
            // User: 50%, Team: 55%, gap = 5% < 20%
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),
                    nonStrategicCommit()
            ));
            when(qualityDataProvider.getTeamStrategicAlignmentRate(eq(ORG_ID), any()))
                    .thenReturn(0.55);

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "ZERO_RCDO_ALIGNMENT");
            assertNoNudgeOfType(result, "BELOW_TEAM_RCDO_ALIGNMENT");
            assertNoNudgeOfType(result, "HIGH_RCDO_ALIGNMENT");
        }

        @Test
        void noNudgeWhenFewerThanMinCommits() {
            stubPlanCommits(List.of(nonStrategicCommit()));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "ZERO_RCDO_ALIGNMENT");
        }
    }

    // ── Urgency/capacity integration ─────────────────────────────────────────

    @Nested
    class UrgencyAndCapacityChecks {

        @Test
        void warningWhenStrategicFocusFallsBelowSlackFloor() {
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),
                    nonStrategicCommit(),
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));
            when(urgencyDataProvider.getStrategicSlack(ORG_ID, USER_ID))
                    .thenReturn(new SlackInfo("LOW_SLACK", new BigDecimal("0.75"), 1, 0));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "URGENCY_FOCUS_GAP");
            assertEquals("WARNING", nudge.severity());
            assertTrue(nudge.message().contains("25%"));
            assertTrue(nudge.message().contains("75%"));
        }

        @Test
        void noUrgencyNudgeWhenStrategicFocusMeetsSlackFloor() {
            stubPlanCommits(List.of(
                    strategicCommit(OUT1_ID),
                    strategicCommit(OUT2_ID),
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));
            when(urgencyDataProvider.getStrategicSlack(ORG_ID, USER_ID))
                    .thenReturn(new SlackInfo("HIGH_SLACK", new BigDecimal("0.50"), 0, 0));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "URGENCY_FOCUS_GAP");
        }

        @Test
        void warningWhenCapacityProviderReturnsModerateOrHighOvercommitment() {
            when(capacityQualityProvider.getOvercommitmentWarning(ORG_ID, PLAN_ID, USER_ID))
                    .thenReturn(java.util.Optional.of(new OvercommitWarning(
                            OvercommitLevel.MODERATE,
                            "Adjusted estimate is 42h against a 36h cap.",
                            BigDecimal.valueOf(42),
                            BigDecimal.valueOf(36)
                    )));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            QualityNudge nudge = assertNudgeOfType(result, "OVERCOMMITMENT_WARNING");
            assertEquals("WARNING", nudge.severity());
            assertTrue(nudge.message().contains("42h"));
        }

        @Test
        void noOvercommitmentNudgeWhenCapacityLevelIsNone() {
            when(capacityQualityProvider.getOvercommitmentWarning(ORG_ID, PLAN_ID, USER_ID))
                    .thenReturn(java.util.Optional.of(new OvercommitWarning(
                            OvercommitLevel.NONE,
                            "",
                            BigDecimal.valueOf(30),
                            BigDecimal.valueOf(36)
                    )));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertNoNudgeOfType(result, "OVERCOMMITMENT_WARNING");
        }

        @Test
        void urgencyProviderFailureDoesNotMakeWholeResultUnavailable() {
            stubPlanCommits(List.of(
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));
            when(urgencyDataProvider.getStrategicSlack(ORG_ID, USER_ID))
                    .thenThrow(new RuntimeException("urgency unavailable"));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertEquals("ok", result.status());
            assertNudgeOfType(result, "ZERO_RCDO_ALIGNMENT");
            assertNoNudgeOfType(result, "URGENCY_FOCUS_GAP");
        }

        @Test
        void capacityProviderFailureDoesNotMakeWholeResultUnavailable() {
            stubPlanCommits(List.of(
                    nonStrategicCommit(),
                    nonStrategicCommit()
            ));
            when(capacityQualityProvider.getOvercommitmentWarning(ORG_ID, PLAN_ID, USER_ID))
                    .thenThrow(new RuntimeException("capacity unavailable"));

            PlanQualityService.QualityCheckResult result =
                    service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

            assertEquals("ok", result.status());
            assertNudgeOfType(result, "ZERO_RCDO_ALIGNMENT");
            assertNoNudgeOfType(result, "OVERCOMMITMENT_WARNING");
        }
    }

    // ── Graceful degradation ──────────────────────────────────────────────────

    @Test
    void returnsUnavailableOnUnexpectedException() {
        when(qualityDataProvider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID))
                .thenThrow(new RuntimeException("Database error"));

        PlanQualityService.QualityCheckResult result =
                service.checkPlanQuality(ORG_ID, PLAN_ID, USER_ID);

        assertEquals("unavailable", result.status());
        assertTrue(result.nudges().isEmpty());
    }

    // ── Assertion helpers ────────────────────────────────────────────────────

    private QualityNudge assertNudgeOfType(PlanQualityService.QualityCheckResult result, String type) {
        return result.nudges().stream()
                .filter(n -> type.equals(n.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected nudge of type " + type + " but got: " + result.nudges()));
    }

    private void assertNoNudgeOfType(PlanQualityService.QualityCheckResult result, String type) {
        boolean found = result.nudges().stream().anyMatch(n -> type.equals(n.type()));
        if (found) {
            throw new AssertionError("Did not expect nudge of type " + type
                    + " but found it in: " + result.nudges());
        }
    }
}
