package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ApiErrorResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link PlanningCopilotController}.
 */
class PlanningCopilotControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    private PlanningCopilotService planningCopilotService;
    private PlanningCopilotDraftApplyService planningCopilotDraftApplyService;
    private AiFeatureFlags featureFlags;
    private RateLimiter rateLimiter;
    private AuthenticatedUserContext authenticatedUserContext;
    private PlanningCopilotController controller;

    @BeforeEach
    void setUp() {
        planningCopilotService = mock(PlanningCopilotService.class);
        planningCopilotDraftApplyService = mock(PlanningCopilotDraftApplyService.class);
        featureFlags = new AiFeatureFlags();
        rateLimiter = new RateLimiter(20, java.time.Duration.ofMinutes(1));
        authenticatedUserContext = new AuthenticatedUserContext();
        loginAs(Set.of("MANAGER"));
        controller = new PlanningCopilotController(
                planningCopilotService,
                planningCopilotDraftApplyService,
                featureFlags,
                rateLimiter,
                authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Set<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, roles),
                        null,
                        List.of()));
    }

    @Test
    void suggestTeamPlanReturnsUnavailableWhenFeatureDisabled() {
        featureFlags.setPlanningCopilotEnabled(false);
        ResponseEntity<?> response = controller.suggestTeamPlan(new PlanningCopilotController.TeamPlanSuggestionRequest("2026-03-23"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PlanningCopilotController.TeamPlanSuggestionUnavailableResponse body =
                (PlanningCopilotController.TeamPlanSuggestionUnavailableResponse) response.getBody();
        assertNotNull(body);
        assertEquals("unavailable", body.status());
        verify(planningCopilotService, never()).suggestTeamPlan(any(), any(), any());
    }

    @Test
    void suggestTeamPlanReturnsSuggestionsWhenEnabled() {
        featureFlags.setPlanningCopilotEnabled(true);
        when(planningCopilotService.suggestTeamPlan(eq(ORG_ID), eq(USER_ID), eq(LocalDate.parse("2026-03-23"))))
                .thenReturn(new PlanningCopilotService.TeamPlanSuggestionResult(
                        "ok",
                        LocalDate.parse("2026-03-23"),
                        new PlanningCopilotService.TeamPlanSummary(
                                new BigDecimal("40.0"),
                                new BigDecimal("30.0"),
                                new BigDecimal("10.0"),
                                0,
                                0,
                                null,
                                "Healthy buffer"),
                        List.of(),
                        List.of(),
                        false));

        ResponseEntity<?> response = controller.suggestTeamPlan(new PlanningCopilotController.TeamPlanSuggestionRequest("2026-03-23"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PlanningCopilotService.TeamPlanSuggestionResult body =
                (PlanningCopilotService.TeamPlanSuggestionResult) response.getBody();
        assertNotNull(body);
        assertEquals("ok", body.status());
    }

    @Test
    void suggestTeamPlanReturnsForbiddenForNonManager() {
        loginAs(Set.of("IC"));

        ResponseEntity<?> response = controller.suggestTeamPlan(new PlanningCopilotController.TeamPlanSuggestionRequest("2026-03-23"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("FORBIDDEN", body.error().code());
        verify(planningCopilotService, never()).suggestTeamPlan(any(), any(), any());
    }

    @Test
    void applyReturnsOkWhenEnabled() {
        featureFlags.setPlanningCopilotEnabled(true);
        UUID reportId = UUID.randomUUID();
        when(planningCopilotDraftApplyService.apply(eq(ORG_ID), eq(USER_ID), eq(LocalDate.parse("2026-03-23")), any()))
                .thenReturn(new PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult(
                        "ok",
                        LocalDate.parse("2026-03-23"),
                        List.of()));

        ResponseEntity<?> response = controller.applyTeamPlanSuggestion(
                new PlanningCopilotController.ApplyTeamPlanSuggestionRequest(
                        "2026-03-23",
                        List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                                reportId.toString(),
                                List.of(new PlanningCopilotController.SuggestedCommitApplyRequest(
                                        "Ship milestone",
                                        UUID.randomUUID().toString(),
                                        "Critical path",
                                        "QUEEN",
                                        6.0))))));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult body =
                (PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult) response.getBody();
        assertNotNull(body);
        assertEquals("ok", body.status());
    }

    @Test
    void applyReturnsUnavailableWhenFeatureDisabled() {
        featureFlags.setPlanningCopilotEnabled(false);
        ResponseEntity<?> response = controller.applyTeamPlanSuggestion(
                new PlanningCopilotController.ApplyTeamPlanSuggestionRequest(
                        "2026-03-23",
                        List.of(new PlanningCopilotController.TeamMemberApplyRequest(UUID.randomUUID().toString(), List.of()))));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PlanningCopilotController.TeamPlanSuggestionUnavailableResponse body =
                (PlanningCopilotController.TeamPlanSuggestionUnavailableResponse) response.getBody();
        assertNotNull(body);
        assertEquals("unavailable", body.status());
    }

    @Test
    void applyReturnsForbiddenForNonManager() {
        loginAs(Set.of("IC"));

        ResponseEntity<?> response = controller.applyTeamPlanSuggestion(
                new PlanningCopilotController.ApplyTeamPlanSuggestionRequest(
                        "2026-03-23",
                        List.of(new PlanningCopilotController.TeamMemberApplyRequest(UUID.randomUUID().toString(), List.of()))));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("FORBIDDEN", body.error().code());
        verify(planningCopilotDraftApplyService, never()).apply(any(), any(), any(), any());
    }

    @Test
    void suggestTeamPlanRejectsMalformedWeekStart() {
        featureFlags.setPlanningCopilotEnabled(true);

        PlanValidationException ex = assertThrows(
                PlanValidationException.class,
                () -> controller.suggestTeamPlan(new PlanningCopilotController.TeamPlanSuggestionRequest("03/23/2026")));

        assertEquals("weekStart must be an ISO-8601 date", ex.getMessage());
    }

    @Test
    void suggestTeamPlanRejectsNullWeekStart() {
        featureFlags.setPlanningCopilotEnabled(true);

        PlanValidationException ex = assertThrows(
                PlanValidationException.class,
                () -> controller.suggestTeamPlan(new PlanningCopilotController.TeamPlanSuggestionRequest(null)));

        assertEquals("weekStart must be an ISO-8601 date", ex.getMessage());
        assertEquals("null", ex.getDetails().getFirst().get("provided"));
    }
}
