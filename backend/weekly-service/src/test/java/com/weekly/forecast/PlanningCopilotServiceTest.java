package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.AiCacheService;
import com.weekly.ai.LlmClient;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.ForecastAnalyticsProvider;
import com.weekly.shared.PredictionDataProvider;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import com.weekly.shared.UserModelDataProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanningCopilotServiceTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("10000000-0000-0000-0000-000000000010");
    private static final UUID ALEX_ID = UUID.fromString("10000000-0000-0000-0000-000000000011");
    private static final UUID SAM_ID = UUID.fromString("10000000-0000-0000-0000-000000000012");
    private static final UUID OUTCOME_CRITICAL = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OUTCOME_STABLE = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-21T10:15:30Z"), ZoneOffset.UTC);

    private OrgGraphClient orgGraphClient;
    private CapacityProfileProvider capacityProfileProvider;
    private UserModelDataProvider userModelDataProvider;
    private UrgencyDataProvider urgencyDataProvider;
    private ForecastAnalyticsProvider forecastAnalyticsProvider;
    private PredictionDataProvider predictionDataProvider;
    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private WeeklyCommitActualRepository weeklyCommitActualRepository;
    private LlmClient llmClient;
    private AiCacheService cacheService;
    private RateLimiter rateLimiter;
    private PlanningCopilotService service;

    @BeforeEach
    void setUp() {
        orgGraphClient = mock(OrgGraphClient.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        userModelDataProvider = mock(UserModelDataProvider.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        forecastAnalyticsProvider = mock(ForecastAnalyticsProvider.class);
        predictionDataProvider = mock(PredictionDataProvider.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        weeklyCommitActualRepository = mock(WeeklyCommitActualRepository.class);
        llmClient = mock(LlmClient.class);
        cacheService = new AiCacheService(Duration.ofHours(1));
        rateLimiter = new RateLimiter(20, Duration.ofMinutes(1));
        service = new PlanningCopilotService(
                orgGraphClient,
                capacityProfileProvider,
                userModelDataProvider,
                urgencyDataProvider,
                forecastAnalyticsProvider,
                predictionDataProvider,
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                llmClient,
                cacheService,
                rateLimiter,
                new ObjectMapper().findAndRegisterModules(),
                FIXED_CLOCK);
    }

    @Test
    void suggestTeamPlanBuildsDeterministicSuggestionsWhenLlmUnavailable() {
        seedTeamContext();
        when(llmClient.complete(any(), any()))
                .thenThrow(new LlmClient.LlmUnavailableException("timeout"));

        PlanningCopilotService.TeamPlanSuggestionResult result =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);

        assertEquals("ok", result.status());
        assertFalse(result.llmRefined());
        assertEquals(2, result.members().size());
        assertTrue(result.summary().teamCapacityHours().compareTo(new BigDecimal("50.0")) >= 0);
        assertTrue(result.summary().bufferHours().compareTo(BigDecimal.ZERO) > 0);

        PlanningCopilotService.MemberPlanSuggestion alex = result.members().stream()
                .filter(member -> member.userId().equals(ALEX_ID.toString()))
                .findFirst()
                .orElseThrow();
        assertTrue(alex.suggestedCommits().stream().anyMatch(commit ->
                commit.title().contains("Pilot follow-up calls")));
        assertTrue(alex.suggestedCommits().stream().anyMatch(commit ->
                OUTCOME_CRITICAL.toString().equals(commit.outcomeId())));
        assertEquals("MODERATE", alex.overcommitRisk());

        PlanningCopilotService.OutcomeAllocationSuggestion topOutcome = result.outcomeAllocations().getFirst();
        assertEquals(OUTCOME_CRITICAL.toString(), topOutcome.outcomeId());
        assertTrue(topOutcome.recommendedHours().compareTo(new BigDecimal("10.0")) >= 0);

        PlanningCopilotService.OutcomeAllocationSuggestion stableOutcome = result.outcomeAllocations().stream()
                .filter(allocation -> allocation.outcomeId().equals(OUTCOME_STABLE.toString()))
                .findFirst()
                .orElseThrow();
        assertTrue(stableOutcome.recommendedHours().compareTo(new BigDecimal("6.0")) <= 0);
    }

    @Test
    void suggestTeamPlanCachesSuccessfulLlmRefinement() {
        seedTeamContext();
        when(llmClient.complete(any(), any())).thenReturn("""
                {
                  "headline": "Protect buffer while concentrating the team on the healthcare pilot.",
                  "members": [
                    {
                      "memberId": "10000000-0000-0000-0000-000000000011",
                      "commits": [
                        {
                          "commitIndex": 0,
                          "title": "Healthcare pilot: close outstanding follow-up calls",
                          "rationale": "Finish the carried item first so the critical healthcare outcome keeps moving without adding more drift."
                        }
                      ]
                    }
                  ]
                }
                """);

        PlanningCopilotService.TeamPlanSuggestionResult first =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);
        PlanningCopilotService.TeamPlanSuggestionResult second =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);

        assertTrue(first.llmRefined());
        assertTrue(second.llmRefined());
        assertEquals("Protect buffer while concentrating the team on the healthcare pilot.", first.summary().headline());
        PlanningCopilotService.MemberPlanSuggestion alex = first.members().stream()
                .filter(member -> member.userId().equals(ALEX_ID.toString()))
                .findFirst()
                .orElseThrow();
        assertEquals("Healthcare pilot: close outstanding follow-up calls", alex.suggestedCommits().getFirst().title());
        verify(llmClient, times(1)).complete(any(), any());
    }

    @Test
    void suggestTeamPlanUsesCachedRefinementEvenWhenLaterCallsAreRateLimited() {
        seedTeamContext();
        rateLimiter = new RateLimiter(1, Duration.ofMinutes(1));
        service = new PlanningCopilotService(
                orgGraphClient,
                capacityProfileProvider,
                userModelDataProvider,
                urgencyDataProvider,
                forecastAnalyticsProvider,
                predictionDataProvider,
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                llmClient,
                cacheService,
                rateLimiter,
                new ObjectMapper().findAndRegisterModules(),
                FIXED_CLOCK);
        when(llmClient.complete(any(), any())).thenReturn("""
                {
                  "headline": "Cacheable planning refinement.",
                  "members": []
                }
                """);

        PlanningCopilotService.TeamPlanSuggestionResult first =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);
        PlanningCopilotService.TeamPlanSuggestionResult second =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);

        assertTrue(first.llmRefined());
        assertTrue(second.llmRefined());
        assertEquals("Cacheable planning refinement.", second.summary().headline());
        verify(llmClient, times(1)).complete(any(), any());
    }

    @Test
    void suggestTeamPlanReturnsEmptyResultWhenManagerHasNoReports() {
        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(List.of());

        PlanningCopilotService.TeamPlanSuggestionResult result =
                service.suggestTeamPlan(ORG_ID, MANAGER_ID, WEEK_START);

        assertEquals("ok", result.status());
        assertTrue(result.members().isEmpty());
        assertEquals(BigDecimal.ZERO, result.summary().teamCapacityHours());
        assertFalse(result.llmRefined());
    }

    private void seedTeamContext() {
        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(List.of(
                new DirectReport(ALEX_ID, "Alex"),
                new DirectReport(SAM_ID, "Sam")));

        when(capacityProfileProvider.getLatestProfile(ORG_ID, ALEX_ID)).thenReturn(Optional.of(
                new CapacityProfileProvider.CapacityProfileSnapshot(
                        ALEX_ID, 8, new BigDecimal("1.20"), new BigDecimal("30.0"), "HIGH", "2026-03-20T00:00:00Z")));
        when(capacityProfileProvider.getLatestProfile(ORG_ID, SAM_ID)).thenReturn(Optional.of(
                new CapacityProfileProvider.CapacityProfileSnapshot(
                        SAM_ID, 8, new BigDecimal("1.00"), new BigDecimal("20.0"), "HIGH", "2026-03-20T00:00:00Z")));

        when(userModelDataProvider.getLatestSnapshot(ORG_ID, ALEX_ID)).thenReturn(Optional.of(
                new UserModelDataProvider.UserModelSnapshot(ALEX_ID, 6, userModelJson(0.82, 0.78, List.of("DELIVERY", "PROJECT")), "2026-03-20T00:00:00Z")));
        when(userModelDataProvider.getLatestSnapshot(ORG_ID, SAM_ID)).thenReturn(Optional.of(
                new UserModelDataProvider.UserModelSnapshot(SAM_ID, 6, userModelJson(0.74, 0.72, List.of("OPERATIONS", "DELIVERY")), "2026-03-20T00:00:00Z")));

        when(predictionDataProvider.getUserPredictions(ORG_ID, ALEX_ID)).thenReturn(List.of(
                new PredictionDataProvider.PredictionSignal("CARRY_FORWARD", true, "HIGH", "Recent slips")));
        when(predictionDataProvider.getUserPredictions(ORG_ID, SAM_ID)).thenReturn(List.of());

        when(urgencyDataProvider.getStrategicSlack(ORG_ID, MANAGER_ID))
                .thenReturn(new SlackInfo("LOW_SLACK", new BigDecimal("0.75"), 1, 1));
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(
                new UrgencyInfo(OUTCOME_CRITICAL, "Healthcare pilot", LocalDate.of(2026, 4, 10),
                        new BigDecimal("35.0"), new BigDecimal("55.0"), "CRITICAL", 20),
                new UrgencyInfo(OUTCOME_STABLE, "Pricing tool", LocalDate.of(2026, 7, 1),
                        new BigDecimal("65.0"), new BigDecimal("60.0"), "ON_TRACK", 100)));

        when(forecastAnalyticsProvider.getOutcomeCoverageHistory(ORG_ID, OUTCOME_CRITICAL, 4))
                .thenReturn(new ForecastAnalyticsProvider.OutcomeCoverageHistory(
                        OUTCOME_CRITICAL,
                        List.of(
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-02", 4, 2, 2),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-09", 3, 2, 1),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-16", 1, 1, 1)),
                        "FALLING"));
        when(forecastAnalyticsProvider.getOutcomeCoverageHistory(ORG_ID, OUTCOME_STABLE, 4))
                .thenReturn(new ForecastAnalyticsProvider.OutcomeCoverageHistory(
                        OUTCOME_STABLE,
                        List.of(
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-02", 2, 1, 1),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-09", 2, 1, 1),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-16", 2, 1, 1)),
                        "STABLE"));

        WeeklyPlanEntity alexPriorPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, ALEX_ID, LocalDate.of(2026, 3, 16));
        alexPriorPlan.setState(PlanState.RECONCILED);
        WeeklyPlanEntity samPriorPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, SAM_ID, LocalDate.of(2026, 3, 16));
        samPriorPlan.setState(PlanState.RECONCILED);

        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                ORG_ID, ALEX_ID, WEEK_START.minusWeeks(6), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(alexPriorPlan));
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                ORG_ID, SAM_ID, WEEK_START.minusWeeks(6), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(samPriorPlan));

        WeeklyCommitEntity alexCarry = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, alexPriorPlan.getId(), "Pilot follow-up calls");
        alexCarry.setOutcomeId(OUTCOME_CRITICAL);
        alexCarry.setChessPriority(ChessPriority.QUEEN);
        alexCarry.setEstimatedHours(new BigDecimal("6.0"));
        WeeklyCommitEntity alexHistory = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, alexPriorPlan.getId(), "Healthcare discovery");
        alexHistory.setOutcomeId(OUTCOME_CRITICAL);
        WeeklyCommitEntity samHistory = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, samPriorPlan.getId(), "Pricing polish");
        samHistory.setOutcomeId(OUTCOME_STABLE);

        WeeklyCommitActualEntity alexCarryActual = new WeeklyCommitActualEntity(alexCarry.getId(), ORG_ID);
        alexCarryActual.setCompletionStatus(CompletionStatus.PARTIALLY);

        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, alexPriorPlan.getId()))
                .thenReturn(List.of(alexCarry, alexHistory));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, samPriorPlan.getId()))
                .thenReturn(List.of(samHistory));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(
                ORG_ID, List.of(alexCarry.getId(), alexHistory.getId())))
                .thenReturn(List.of(alexCarryActual));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(
                ORG_ID, List.of(samHistory.getId())))
                .thenReturn(List.of());
    }

    private String userModelJson(double completionReliability, double estimationAccuracy, List<String> topCategories) {
        String categoryRates = topCategories.stream()
                .map(category -> "\"" + category + "\":0.8")
                .collect(java.util.stream.Collectors.joining(","));
        return "{" +
                "\"performanceProfile\":{" +
                "\"completionReliability\":" + completionReliability + "," +
                "\"estimationAccuracy\":" + estimationAccuracy + "," +
                "\"topCategories\":[\"" + String.join("\",\"", topCategories) + "\"]," +
                "\"categoryCompletionRates\":{" + categoryRates + "}" +
                "}}";
    }
}
