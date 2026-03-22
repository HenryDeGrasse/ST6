package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.forecast.ForecastDtos.OutcomeForecastResponse;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.ForecastAnalyticsProvider;
import com.weekly.shared.PredictionDataProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Unit tests for {@link TargetDateForecastService}.
 */
class TargetDateForecastServiceTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OUTCOME_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CONTRIBUTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-21T10:15:30Z"), ZoneOffset.UTC);

    private LatestForecastRepository latestForecastRepository;
    private UrgencyDataProvider urgencyDataProvider;
    private ForecastAnalyticsProvider forecastAnalyticsProvider;
    private CapacityProfileProvider capacityProfileProvider;
    private PredictionDataProvider predictionDataProvider;
    private WeeklyPlanRepository weeklyPlanRepository;
    private RcdoClient rcdoClient;
    private JdbcTemplate jdbcTemplate;
    private TargetDateForecastService service;

    @BeforeEach
    void setUp() {
        latestForecastRepository = mock(LatestForecastRepository.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        forecastAnalyticsProvider = mock(ForecastAnalyticsProvider.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        predictionDataProvider = mock(PredictionDataProvider.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        rcdoClient = mock(RcdoClient.class);
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:forecast-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", ""));
        jdbcTemplate.execute("DROP TABLE IF EXISTS weekly_commits");
        jdbcTemplate.execute("DROP TABLE IF EXISTS weekly_plans");
        jdbcTemplate.execute("CREATE TABLE weekly_plans ("
                + "id UUID PRIMARY KEY, "
                + "org_id UUID NOT NULL, "
                + "owner_user_id UUID NOT NULL, "
                + "week_start_date DATE NOT NULL, "
                + "deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute("CREATE TABLE weekly_commits ("
                + "id UUID PRIMARY KEY, "
                + "org_id UUID NOT NULL, "
                + "weekly_plan_id UUID NOT NULL, "
                + "outcome_id UUID NULL, "
                + "deleted_at TIMESTAMP NULL)");
        service = new TargetDateForecastService(
                latestForecastRepository,
                urgencyDataProvider,
                forecastAnalyticsProvider,
                capacityProfileProvider,
                predictionDataProvider,
                weeklyPlanRepository,
                rcdoClient,
                new ObjectMapper().findAndRegisterModules(),
                jdbcTemplate,
                FIXED_CLOCK);
    }

    @Test
    void computeForecastUsesCompositeSignalsAndPersistsForecast() {
        jdbcTemplate.update(
                "INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, deleted_at) VALUES (?, ?, ?, ?, NULL)",
                UUID.randomUUID(), ORG_ID, CONTRIBUTOR_ID, LocalDate.of(2026, 3, 16));
        UUID planId = jdbcTemplate.queryForObject(
                "SELECT id FROM weekly_plans WHERE org_id = ?", UUID.class, ORG_ID);
        jdbcTemplate.update(
                "INSERT INTO weekly_commits (id, org_id, weekly_plan_id, outcome_id, deleted_at) VALUES (?, ?, ?, ?, NULL)",
                UUID.randomUUID(), ORG_ID, planId, OUTCOME_ID);

        UrgencyInfo urgencyInfo = new UrgencyInfo(
                OUTCOME_ID,
                "Improve activation",
                LocalDate.of(2026, 4, 18),
                new BigDecimal("45.00"),
                new BigDecimal("60.00"),
                "AT_RISK",
                28L);
        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, OUTCOME_ID)).thenReturn(urgencyInfo);
        when(forecastAnalyticsProvider.getOutcomeCoverageHistory(ORG_ID, OUTCOME_ID, TargetDateForecastService.DEFAULT_HISTORY_WEEKS))
                .thenReturn(new ForecastAnalyticsProvider.OutcomeCoverageHistory(
                        OUTCOME_ID,
                        List.of(
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-02", 3, 2, 1),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-09", 2, 1, 1),
                                new ForecastAnalyticsProvider.OutcomeCoveragePoint("2026-03-16", 1, 1, 0)),
                        "FALLING"));
        when(capacityProfileProvider.getLatestProfile(ORG_ID, CONTRIBUTOR_ID))
                .thenReturn(Optional.of(new CapacityProfileProvider.CapacityProfileSnapshot(
                        CONTRIBUTOR_ID,
                        8,
                        new BigDecimal("1.30"),
                        new BigDecimal("28.0"),
                        "LOW",
                        "2026-03-18T00:00:00Z")));
        when(predictionDataProvider.getUserPredictions(ORG_ID, CONTRIBUTOR_ID))
                .thenReturn(List.of(new PredictionDataProvider.PredictionSignal(
                        "CARRY_FORWARD",
                        true,
                        "HIGH",
                        "Likely to carry work forward again")));
        when(latestForecastRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID)).thenReturn(Optional.empty());
        when(latestForecastRepository.save(any(LatestForecastEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LatestForecastEntity> saved = service.computeAndPersistOutcomeForecast(ORG_ID, OUTCOME_ID);

        assertTrue(saved.isPresent());
        LatestForecastEntity entity = saved.get();
        assertEquals(TargetDateForecastService.STATUS_AT_RISK, entity.getForecastStatus());
        assertEquals(TargetDateForecastService.MODEL_VERSION, entity.getModelVersion());
        assertNotNull(entity.getProjectedTargetDate());
        assertTrue(entity.getConfidenceScore().compareTo(new BigDecimal("0.50")) >= 0);
        assertTrue(entity.getForecastDetailsJson().contains("Carry-forward risk detected"));
        assertTrue(entity.getForecastDetailsJson().contains("Contributor capacity risk"));
    }

    @Test
    void getOrComputeOutcomeForecastReturnsNotFoundWhenOutcomeIsNotTracked() {
        when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, OUTCOME_ID)).thenReturn(null);

        Optional<OutcomeForecastResponse> response = service.getOrComputeOutcomeForecast(ORG_ID, OUTCOME_ID);

        assertTrue(response.isEmpty());
        verify(latestForecastRepository).deleteByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID);
    }

    @Test
    void getOrComputeOrgForecastsMapsPersistedEntityToResponse() {
        LatestForecastEntity entity = persistedForecast("{\"targetDate\":\"2026-04-12\"}");
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Improve activation",
                LocalDate.of(2026, 4, 12),
                new BigDecimal("70.00"),
                new BigDecimal("68.00"),
                "ON_TRACK",
                22L)));
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of(entity));

        List<OutcomeForecastResponse> responses = service.getOrComputeOrgForecasts(ORG_ID);

        assertEquals(1, responses.size());
        OutcomeForecastResponse response = responses.getFirst();
        assertEquals("Improve activation", response.outcomeName());
        assertEquals(LocalDate.of(2026, 4, 12), response.targetDate());
        assertEquals("HIGH", response.confidenceBand());
        assertEquals("Coverage trend is rising", response.contributingFactors().getFirst().label());
        assertEquals("Maintain momentum", response.recommendations().getFirst());
        assertEquals("Improve activation", assertInstanceOf(String.class, response.outcomeName()));
    }

    @Test
    void getOrComputeOrgForecastsParsesArrayShapedPersistedTargetDate() {
        LatestForecastEntity entity = persistedForecast("{\"targetDate\":[2026,4,12]}");
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Improve activation",
                LocalDate.of(2026, 4, 12),
                new BigDecimal("70.00"),
                new BigDecimal("68.00"),
                "ON_TRACK",
                22L)));
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of(entity));

        List<OutcomeForecastResponse> responses = service.getOrComputeOrgForecasts(ORG_ID);

        assertEquals(LocalDate.of(2026, 4, 12), responses.getFirst().targetDate());
    }

    @Test
    void getOrComputeOrgForecastsComputesMissingTrackedForecastsAndRemovesStaleRows() {
        UUID staleOutcomeId = UUID.fromString("20000000-0000-0000-0000-000000000099");
        LatestForecastEntity stale = new LatestForecastEntity(ORG_ID, staleOutcomeId);
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Improve activation",
                LocalDate.of(2026, 4, 18),
                new BigDecimal("45.00"),
                new BigDecimal("60.00"),
                "AT_RISK",
                28L)));
        when(latestForecastRepository.findByOrgId(ORG_ID)).thenReturn(List.of(stale));
        when(latestForecastRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID)).thenReturn(Optional.empty());
        when(forecastAnalyticsProvider.getOutcomeCoverageHistory(ORG_ID, OUTCOME_ID, TargetDateForecastService.DEFAULT_HISTORY_WEEKS))
                .thenReturn(new ForecastAnalyticsProvider.OutcomeCoverageHistory(OUTCOME_ID, List.of(), "STABLE"));
        when(latestForecastRepository.save(any(LatestForecastEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<OutcomeForecastResponse> responses = service.getOrComputeOrgForecasts(ORG_ID);

        assertEquals(1, responses.size());
        assertEquals(OUTCOME_ID.toString(), responses.getFirst().outcomeId());
        verify(latestForecastRepository).deleteByOrgIdAndOutcomeId(ORG_ID, staleOutcomeId);
    }

    private static LatestForecastEntity persistedForecast(String forecastInputsJson) {
        LatestForecastEntity entity = new LatestForecastEntity(ORG_ID, OUTCOME_ID);
        entity.setForecastStatus(TargetDateForecastService.STATUS_ON_TRACK);
        entity.setModelVersion(TargetDateForecastService.MODEL_VERSION);
        entity.setProjectedTargetDate(LocalDate.of(2026, 4, 10));
        entity.setProjectedProgressPct(new BigDecimal("88.50"));
        entity.setProjectedVelocity(new BigDecimal("7.2500"));
        entity.setConfidenceScore(new BigDecimal("0.8100"));
        entity.setForecastInputsJson(forecastInputsJson);
        entity.setForecastDetailsJson("{"
                + "\"confidenceBand\":\"HIGH\","
                + "\"outcomeName\":\"Improve activation\","
                + "\"contributingFactors\":[{\"type\":\"coverage\",\"label\":\"Coverage trend is rising\",\"score\":0.8,\"detail\":\"healthy\"}],"
                + "\"recommendations\":[\"Maintain momentum\"]}");
        return entity;
    }
}
