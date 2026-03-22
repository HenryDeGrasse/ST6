package com.weekly.executive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgRosterEntry;
import com.weekly.auth.OrgTeamGroup;
import com.weekly.capacity.CapacityProfileEntity;
import com.weekly.capacity.CapacityProfileRepository;
import com.weekly.forecast.LatestForecastEntity;
import com.weekly.forecast.LatestForecastRepository;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutiveDashboardServiceTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_A = UUID.fromString("10000000-0000-0000-0000-000000000010");
    private static final UUID MANAGER_B = UUID.fromString("10000000-0000-0000-0000-000000000020");
    private static final UUID USER_A = UUID.fromString("10000000-0000-0000-0000-000000000011");
    private static final UUID USER_B = UUID.fromString("10000000-0000-0000-0000-000000000012");
    private static final UUID USER_C = UUID.fromString("10000000-0000-0000-0000-000000000021");
    private static final UUID OUTCOME_1 = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OUTCOME_2 = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID RALLY_1 = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RALLY_2 = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);

    private LatestForecastRepository latestForecastRepository;
    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private CapacityProfileRepository capacityProfileRepository;
    private OrgGraphClient orgGraphClient;
    private RcdoClient rcdoClient;
    private ExecutiveDashboardService service;

    @BeforeEach
    void setUp() {
        latestForecastRepository = mock(LatestForecastRepository.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        capacityProfileRepository = mock(CapacityProfileRepository.class);
        orgGraphClient = mock(OrgGraphClient.class);
        rcdoClient = mock(RcdoClient.class);
        service = new ExecutiveDashboardService(
                latestForecastRepository,
                weeklyPlanRepository,
                weeklyCommitRepository,
                capacityProfileRepository,
                orgGraphClient,
                rcdoClient,
                Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aggregatesForecastCapacityAndOpaqueTeamBuckets() {
        LatestForecastEntity forecast1 = new LatestForecastEntity(ORG_ID, OUTCOME_1);
        forecast1.setForecastStatus("ON_TRACK");
        forecast1.setConfidenceScore(new BigDecimal("0.80"));
        LatestForecastEntity forecast2 = new LatestForecastEntity(ORG_ID, OUTCOME_2);
        forecast2.setForecastStatus("NEEDS_ATTENTION");
        forecast2.setConfidenceScore(new BigDecimal("0.60"));
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of(forecast1, forecast2));

        WeeklyPlanEntity planA = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_A, WEEK_START);
        WeeklyPlanEntity planB = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_B, WEEK_START);
        WeeklyPlanEntity planC = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_C, WEEK_START);
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(ORG_ID, WEEK_START, WEEK_START))
                .thenReturn(List.of(planA, planB, planC));

        WeeklyCommitEntity strategicA = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planA.getId(), "Advance outcome 1");
        strategicA.setOutcomeId(OUTCOME_1);
        strategicA.setEstimatedHours(new BigDecimal("10.0"));
        WeeklyCommitEntity nonStrategicA = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planA.getId(), "Support queue");
        nonStrategicA.setEstimatedHours(new BigDecimal("5.0"));
        WeeklyCommitEntity strategicB = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planB.getId(), "Advance outcome 2");
        strategicB.setOutcomeId(OUTCOME_2);
        strategicB.setEstimatedHours(new BigDecimal("8.0"));
        WeeklyCommitEntity strategicC = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planC.getId(), "Advance outcome 1 again");
        strategicC.setOutcomeId(OUTCOME_1);
        strategicC.setEstimatedHours(new BigDecimal("12.0"));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planA.getId(), planB.getId(), planC.getId())))
                .thenReturn(List.of(strategicA, nonStrategicA, strategicB, strategicC));

        when(capacityProfileRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                profile(USER_A, "30.0"),
                profile(USER_B, "20.0"),
                profile(USER_C, "25.0")));

        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of(
                new OrgRosterEntry(USER_A, "User A", MANAGER_A, "UTC"),
                new OrgRosterEntry(USER_B, "User B", MANAGER_A, "UTC"),
                new OrgRosterEntry(USER_C, "User C", MANAGER_B, "UTC")));
        when(orgGraphClient.getOrgTeamGroups(ORG_ID)).thenReturn(Map.of(
                MANAGER_A, new OrgTeamGroup(MANAGER_A, "Manager A", List.of(
                        new OrgRosterEntry(USER_A, "User A", MANAGER_A, "UTC"),
                        new OrgRosterEntry(USER_B, "User B", MANAGER_A, "UTC"))),
                MANAGER_B, new OrgTeamGroup(MANAGER_B, "Manager B", List.of(
                        new OrgRosterEntry(USER_C, "User C", MANAGER_B, "UTC")))));

        when(rcdoClient.getTree(ORG_ID)).thenReturn(new RcdoTree(List.of(
                new RcdoTree.RallyCry(
                        RALLY_1.toString(),
                        "Customer Growth",
                        List.of(new RcdoTree.Objective(
                                UUID.randomUUID().toString(),
                                "Improve activation",
                                RALLY_1.toString(),
                                List.of(new RcdoTree.Outcome(
                                        OUTCOME_1.toString(),
                                        "Outcome 1",
                                        UUID.randomUUID().toString()))))),
                new RcdoTree.RallyCry(
                        RALLY_2.toString(),
                        "Platform Stability",
                        List.of(new RcdoTree.Objective(
                                UUID.randomUUID().toString(),
                                "Reduce incidents",
                                RALLY_2.toString(),
                                List.of(new RcdoTree.Outcome(
                                        OUTCOME_2.toString(),
                                        "Outcome 2",
                                        UUID.randomUUID().toString()))))))));

        ExecutiveDashboardService.ExecutiveDashboardResult result = service.getStrategicHealth(ORG_ID, WEEK_START);

        assertEquals(WEEK_START, result.weekStart());
        assertEquals(new BigDecimal("30.0"), result.summary().strategicHours());
        assertEquals(new BigDecimal("5.0"), result.summary().nonStrategicHours());
        assertEquals(new BigDecimal("40.00"), result.summary().strategicCapacityUtilizationPct());
        assertEquals(new BigDecimal("6.67"), result.summary().nonStrategicCapacityUtilizationPct());
        assertEquals(new BigDecimal("0.7000"), result.summary().averageForecastConfidence());
        assertTrue(result.teamGroupingAvailable());
        assertEquals(2, result.teamBuckets().size());
        assertEquals("team-1", result.teamBuckets().getFirst().bucketId());
        assertEquals(2, result.teamBuckets().getFirst().memberCount());
        assertEquals(new BigDecimal("36.00"), result.teamBuckets().getFirst().strategicCapacityUtilizationPct());
        assertEquals(2, result.rallyCryRollups().size());
        assertEquals("Platform Stability", result.rallyCryRollups().getFirst().rallyCryName());
        assertEquals(new BigDecimal("8.0"), result.rallyCryRollups().getFirst().strategicHours());
    }

    @Test
    void usesWholeRosterCapacityForOrgUtilizationEvenWhenSomeUsersHaveNoPlan() {
        LatestForecastEntity forecast = new LatestForecastEntity(ORG_ID, OUTCOME_1);
        forecast.setForecastStatus("ON_TRACK");
        forecast.setConfidenceScore(new BigDecimal("0.90"));
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of(forecast));

        WeeklyPlanEntity planA = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_A, WEEK_START);
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(ORG_ID, WEEK_START, WEEK_START))
                .thenReturn(List.of(planA));

        WeeklyCommitEntity strategicA = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planA.getId(), "Advance outcome 1");
        strategicA.setOutcomeId(OUTCOME_1);
        strategicA.setEstimatedHours(new BigDecimal("10.0"));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planA.getId())))
                .thenReturn(List.of(strategicA));

        when(capacityProfileRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                profile(USER_A, "30.0"),
                profile(USER_B, "20.0"),
                profile(USER_C, "25.0")));

        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of(
                new OrgRosterEntry(USER_A, "User A", MANAGER_A, "UTC"),
                new OrgRosterEntry(USER_B, "User B", MANAGER_A, "UTC"),
                new OrgRosterEntry(USER_C, "User C", MANAGER_B, "UTC")));
        when(orgGraphClient.getOrgTeamGroups(ORG_ID)).thenReturn(Map.of());
        when(rcdoClient.getTree(ORG_ID)).thenReturn(new RcdoTree(List.of()));

        ExecutiveDashboardService.ExecutiveDashboardResult result = service.getStrategicHealth(ORG_ID, WEEK_START);

        assertEquals(new BigDecimal("75.0"), result.summary().totalCapacityHours());
        assertEquals(new BigDecimal("13.33"), result.summary().strategicCapacityUtilizationPct());
        assertEquals(new BigDecimal("33.33"), result.summary().planningCoveragePct());
    }

    @Test
    void fallsBackToCapacityProfilesForPlanningCoverageWhenRosterUnavailable() {
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
        WeeklyPlanEntity planA = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_A, WEEK_START);
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(ORG_ID, WEEK_START, WEEK_START))
                .thenReturn(List.of(planA));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planA.getId())))
                .thenReturn(List.of());
        when(capacityProfileRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                profile(USER_A, "30.0"),
                profile(USER_B, "20.0"),
                profile(USER_C, "25.0")));
        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of());
        when(orgGraphClient.getOrgTeamGroups(ORG_ID)).thenReturn(Map.of());
        when(rcdoClient.getTree(ORG_ID)).thenReturn(new RcdoTree(List.of()));

        ExecutiveDashboardService.ExecutiveDashboardResult result = service.getStrategicHealth(ORG_ID, WEEK_START);

        assertEquals(new BigDecimal("33.33"), result.summary().planningCoveragePct());
    }

    @Test
    void mapsPhaseFiveForecastStatusesIntoExecutiveHealthBuckets() {
        LatestForecastEntity complete = new LatestForecastEntity(ORG_ID, OUTCOME_1);
        complete.setForecastStatus("COMPLETE");
        complete.setConfidenceScore(new BigDecimal("0.90"));

        LatestForecastEntity atRisk = new LatestForecastEntity(ORG_ID, OUTCOME_2);
        atRisk.setForecastStatus("AT_RISK");
        atRisk.setConfidenceScore(new BigDecimal("0.40"));

        LatestForecastEntity needsAttention = new LatestForecastEntity(ORG_ID, UUID.randomUUID());
        needsAttention.setForecastStatus("NEEDS_ATTENTION");

        LatestForecastEntity noTargetDate = new LatestForecastEntity(ORG_ID, UUID.randomUUID());
        noTargetDate.setForecastStatus("NO_TARGET_DATE");

        when(latestForecastRepository.findByOrgId(ORG_ID))
                .thenReturn(List.of(complete, atRisk, needsAttention, noTargetDate));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(ORG_ID, WEEK_START, WEEK_START))
                .thenReturn(List.of());
        when(capacityProfileRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of());
        when(orgGraphClient.getOrgTeamGroups(ORG_ID)).thenReturn(Map.of());
        when(rcdoClient.getTree(ORG_ID)).thenReturn(new RcdoTree(List.of()));

        ExecutiveDashboardService.ExecutiveDashboardResult result = service.getStrategicHealth(ORG_ID, WEEK_START);

        assertEquals(1, result.summary().onTrackForecasts());
        assertEquals(1, result.summary().offTrackForecasts());
        assertEquals(1, result.summary().needsAttentionForecasts());
        assertEquals(1, result.summary().noDataForecasts());
    }

    private CapacityProfileEntity profile(UUID userId, String cap) {
        CapacityProfileEntity entity = new CapacityProfileEntity(ORG_ID, userId);
        entity.setRealisticWeeklyCap(new BigDecimal(cap));
        return entity;
    }
}
