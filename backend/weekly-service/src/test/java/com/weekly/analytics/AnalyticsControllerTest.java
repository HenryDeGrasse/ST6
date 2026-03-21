package com.weekly.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.weekly.analytics.dto.CarryForwardHeatmap;
import com.weekly.analytics.dto.CategoryShift;
import com.weekly.analytics.dto.HeatmapCell;
import com.weekly.analytics.dto.HeatmapUser;
import com.weekly.analytics.dto.OutcomeCoverageTimeline;
import com.weekly.analytics.dto.OutcomeCoverageWeek;
import com.weekly.analytics.dto.UserCategoryShift;
import com.weekly.analytics.dto.UserEstimationAccuracy;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AnalyticsController}: manager authorization,
 * weeks validation, and service delegation.
 */
class AnalyticsControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID TEAM_MEMBER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");

    private TestAnalyticsService analyticsService;
    private AuthenticatedUserContext authenticatedUserContext;
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        analyticsService = new TestAnalyticsService();
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new AnalyticsController(analyticsService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(MANAGER_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()
                )
        );
    }

    private void loginAsNonManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(MANAGER_ID, ORG_ID, Set.of("INDIVIDUAL_CONTRIBUTOR")),
                        null,
                        List.of()
                )
        );
    }

    private OutcomeCoverageTimeline stubOutcomeCoverage() {
        return new OutcomeCoverageTimeline(
                List.of(new OutcomeCoverageWeek("2026-03-16", 4, 2, 1)),
                AnalyticsService.TREND_STABLE
        );
    }

    private CarryForwardHeatmap stubCarryForwardHeatmap() {
        return new CarryForwardHeatmap(
                List.of(new HeatmapUser(
                        TEAM_MEMBER_ID.toString(),
                        "Ada Lovelace",
                        List.of(new HeatmapCell("2026-03-16", 2))
                ))
        );
    }

    private List<UserCategoryShift> stubCategoryShifts() {
        return List.of(new UserCategoryShift(
                TEAM_MEMBER_ID.toString(),
                Map.of("DELIVERY", 0.75),
                Map.of("DELIVERY", 0.50),
                new CategoryShift("DELIVERY", 0.25)
        ));
    }

    private List<UserEstimationAccuracy> stubEstimationAccuracy() {
        return List.of(new UserEstimationAccuracy(TEAM_MEMBER_ID.toString(), 0.8, 0.7, 0.1));
    }

    @Nested
    class OutcomeCoverageEndpoint {

        @Test
        void returnsOkForManager() {
            loginAsManager();
            OutcomeCoverageTimeline stub = stubOutcomeCoverage();
            analyticsService.outcomeCoverageResponse = stub;

            ResponseEntity<?> response = controller.getOutcomeCoverage(OUTCOME_ID, 8);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
        }

        @Test
        void returnsForbiddenForNonManager() {
            loginAsNonManager();

            ResponseEntity<?> response = controller.getOutcomeCoverage(OUTCOME_ID, 8);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            ApiErrorResponse error = (ApiErrorResponse) response.getBody();
            assertNotNull(error);
            assertEquals("FORBIDDEN", error.error().code());
            assertNull(analyticsService.lastOutcomeCoverageOrgId);
        }

        @Test
        void rejectsWeeksOutsideAllowedRange() {
            loginAsManager();

            ResponseEntity<?> response = controller.getOutcomeCoverage(OUTCOME_ID, 27);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            ApiErrorResponse error = (ApiErrorResponse) response.getBody();
            assertNotNull(error);
            assertEquals("VALIDATION_ERROR", error.error().code());
            assertNull(analyticsService.lastOutcomeCoverageOrgId);
        }

        @Test
        void delegatesOutcomeCoverageWithOrgId() {
            loginAsManager();
            analyticsService.outcomeCoverageResponse = stubOutcomeCoverage();

            controller.getOutcomeCoverage(OUTCOME_ID, 6);

            assertEquals(ORG_ID, analyticsService.lastOutcomeCoverageOrgId);
            assertEquals(OUTCOME_ID, analyticsService.lastOutcomeCoverageOutcomeId);
            assertEquals(6, analyticsService.lastOutcomeCoverageWeeks);
        }
    }

    @Nested
    class CarryForwardHeatmapEndpoint {

        @Test
        void delegatesCarryForwardHeatmapWithManagerContext() {
            loginAsManager();
            CarryForwardHeatmap stub = stubCarryForwardHeatmap();
            analyticsService.carryForwardHeatmapResponse = stub;

            ResponseEntity<?> response = controller.getCarryForwardHeatmap(8);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
            assertEquals(ORG_ID, analyticsService.lastCarryForwardOrgId);
            assertEquals(MANAGER_ID, analyticsService.lastCarryForwardManagerId);
            assertEquals(8, analyticsService.lastCarryForwardWeeks);
        }

        @Test
        void blocksCarryForwardHeatmapForNonManager() {
            loginAsNonManager();

            ResponseEntity<?> response = controller.getCarryForwardHeatmap(8);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNull(analyticsService.lastCarryForwardOrgId);
        }
    }

    @Nested
    class CategoryShiftsEndpoint {

        @Test
        void delegatesCategoryShiftAnalysisWithManagerContext() {
            loginAsManager();
            List<UserCategoryShift> stub = stubCategoryShifts();
            analyticsService.categoryShiftResponse = stub;

            ResponseEntity<?> response = controller.getCategoryShifts(10);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
            assertEquals(ORG_ID, analyticsService.lastCategoryShiftOrgId);
            assertEquals(MANAGER_ID, analyticsService.lastCategoryShiftManagerId);
            assertEquals(10, analyticsService.lastCategoryShiftWeeks);
        }

        @Test
        void rejectsCategoryShiftsWeeksBelowMin() {
            loginAsManager();

            ResponseEntity<?> response = controller.getCategoryShifts(0);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            assertNull(analyticsService.lastCategoryShiftOrgId);
        }
    }

    @Nested
    class EstimationAccuracyEndpoint {

        @Test
        void delegatesEstimationAccuracyWithManagerContext() {
            loginAsManager();
            List<UserEstimationAccuracy> stub = stubEstimationAccuracy();
            analyticsService.estimationAccuracyResponse = stub;

            ResponseEntity<?> response = controller.getEstimationAccuracy(12);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
            assertEquals(ORG_ID, analyticsService.lastEstimationAccuracyOrgId);
            assertEquals(MANAGER_ID, analyticsService.lastEstimationAccuracyManagerId);
            assertEquals(12, analyticsService.lastEstimationAccuracyWeeks);
        }

        @Test
        void blocksEstimationAccuracyForNonManager() {
            loginAsNonManager();

            ResponseEntity<?> response = controller.getEstimationAccuracy(8);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNull(analyticsService.lastEstimationAccuracyOrgId);
        }
    }

    private static final class TestAnalyticsService extends AnalyticsService {

        private UUID lastOutcomeCoverageOrgId;
        private UUID lastOutcomeCoverageOutcomeId;
        private Integer lastOutcomeCoverageWeeks;
        private OutcomeCoverageTimeline outcomeCoverageResponse = new OutcomeCoverageTimeline(List.of(), TREND_STABLE);

        private UUID lastCarryForwardOrgId;
        private UUID lastCarryForwardManagerId;
        private Integer lastCarryForwardWeeks;
        private CarryForwardHeatmap carryForwardHeatmapResponse = new CarryForwardHeatmap(List.of());

        private UUID lastCategoryShiftOrgId;
        private UUID lastCategoryShiftManagerId;
        private Integer lastCategoryShiftWeeks;
        private List<UserCategoryShift> categoryShiftResponse = List.of();

        private UUID lastEstimationAccuracyOrgId;
        private UUID lastEstimationAccuracyManagerId;
        private Integer lastEstimationAccuracyWeeks;
        private List<UserEstimationAccuracy> estimationAccuracyResponse = List.of();

        private TestAnalyticsService() {
            super(new JdbcTemplate(), new NoOpOrgGraphClient());
        }

        @Override
        public OutcomeCoverageTimeline getOutcomeCoverageTimeline(UUID orgId, UUID outcomeId, int weeks) {
            lastOutcomeCoverageOrgId = orgId;
            lastOutcomeCoverageOutcomeId = outcomeId;
            lastOutcomeCoverageWeeks = weeks;
            return outcomeCoverageResponse;
        }

        @Override
        public CarryForwardHeatmap getTeamCarryForwardHeatmap(UUID orgId, UUID managerId, int weeks) {
            lastCarryForwardOrgId = orgId;
            lastCarryForwardManagerId = managerId;
            lastCarryForwardWeeks = weeks;
            return carryForwardHeatmapResponse;
        }

        @Override
        public List<UserCategoryShift> getCategoryShiftAnalysis(UUID orgId, UUID managerId, int weeks) {
            lastCategoryShiftOrgId = orgId;
            lastCategoryShiftManagerId = managerId;
            lastCategoryShiftWeeks = weeks;
            return categoryShiftResponse;
        }

        @Override
        public List<UserEstimationAccuracy> getEstimationAccuracyDistribution(UUID orgId, UUID managerId, int weeks) {
            lastEstimationAccuracyOrgId = orgId;
            lastEstimationAccuracyManagerId = managerId;
            lastEstimationAccuracyWeeks = weeks;
            return estimationAccuracyResponse;
        }
    }

    private static final class NoOpOrgGraphClient implements OrgGraphClient {
        @Override
        public List<UUID> getDirectReports(UUID orgId, UUID managerId) {
            return List.of();
        }
    }
}
