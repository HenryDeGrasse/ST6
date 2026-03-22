package com.weekly.executive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
import java.time.Duration;
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

class ExecutiveBriefingControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    private ExecutiveDashboardService executiveDashboardService;
    private ExecutiveBriefingService executiveBriefingService;
    private AiFeatureFlags featureFlags;
    private RateLimiter rateLimiter;
    private AuthenticatedUserContext authenticatedUserContext;
    private ExecutiveBriefingController controller;

    @BeforeEach
    void setUp() {
        executiveDashboardService = mock(ExecutiveDashboardService.class);
        executiveBriefingService = mock(ExecutiveBriefingService.class);
        featureFlags = new AiFeatureFlags();
        rateLimiter = new RateLimiter(20, Duration.ofMinutes(1));
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new ExecutiveBriefingController(
                executiveDashboardService,
                executiveBriefingService,
                featureFlags,
                rateLimiter,
                authenticatedUserContext);
        loginAs(Set.of("ADMIN"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUnavailableWhenFeatureDisabled() {
        featureFlags.setExecutiveDashboardEnabled(false);
        ResponseEntity<?> response = controller.executiveBriefing(
                new ExecutiveBriefingController.ExecutiveBriefingRequest("2026-03-23"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExecutiveBriefingService.ExecutiveBriefingResult body =
                assertInstanceOf(ExecutiveBriefingService.ExecutiveBriefingResult.class, response.getBody());
        assertEquals("unavailable", body.status());
        verify(executiveDashboardService, never()).getStrategicHealth(any(), any());
    }

    @Test
    void returnsBriefingWhenEnabled() {
        featureFlags.setExecutiveDashboardEnabled(true);
        ExecutiveDashboardService.ExecutiveDashboardResult dashboard = new ExecutiveDashboardService.ExecutiveDashboardResult(
                LocalDate.of(2026, 3, 23),
                new ExecutiveDashboardService.ExecutiveSummary(
                        1, 1, 0, 0, 0,
                        java.math.BigDecimal.ONE,
                        new java.math.BigDecimal("40.0"),
                        new java.math.BigDecimal("30.0"),
                        new java.math.BigDecimal("10.0"),
                        new java.math.BigDecimal("75.00"),
                        new java.math.BigDecimal("25.00"),
                        new java.math.BigDecimal("100.00")),
                List.of(),
                List.of(),
                true);
        when(executiveDashboardService.getStrategicHealth(ORG_ID, LocalDate.of(2026, 3, 23))).thenReturn(dashboard);
        when(executiveBriefingService.createBriefing(ORG_ID, dashboard)).thenReturn(
                new ExecutiveBriefingService.ExecutiveBriefingResult(
                        "ok",
                        "Strategic capacity is concentrated in tracked outcomes.",
                        List.of(
                                new ExecutiveBriefingService.ExecutiveBriefingItem(
                                        "Focus is healthy",
                                        "Strategic utilization remains above 70%.",
                                        "POSITIVE"),
                                new ExecutiveBriefingService.ExecutiveBriefingItem(
                                        "Forecast attention remains",
                                        "Some forecasted work still needs active intervention.",
                                        "WARNING"))));

        ResponseEntity<?> response = controller.executiveBriefing(
                new ExecutiveBriefingController.ExecutiveBriefingRequest("2026-03-23"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExecutiveBriefingService.ExecutiveBriefingResult body =
                assertInstanceOf(ExecutiveBriefingService.ExecutiveBriefingResult.class, response.getBody());
        assertEquals("ok", body.status());
        assertEquals(2, body.insights().size());
    }

    @Test
    void returnsForbiddenForIc() {
        featureFlags.setExecutiveDashboardEnabled(true);
        loginAs(Set.of("IC"));

        ResponseEntity<?> response = controller.executiveBriefing(
                new ExecutiveBriefingController.ExecutiveBriefingRequest("2026-03-23"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("FORBIDDEN", body.error().code());
    }

    private void loginAs(Set<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, roles),
                        null,
                        List.of()));
    }
}
