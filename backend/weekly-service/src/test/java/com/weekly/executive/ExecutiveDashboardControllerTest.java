package com.weekly.executive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
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

class ExecutiveDashboardControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    private ExecutiveDashboardService executiveDashboardService;
    private AuthenticatedUserContext authenticatedUserContext;
    private AiFeatureFlags featureFlags;
    private ExecutiveDashboardController controller;

    @BeforeEach
    void setUp() {
        executiveDashboardService = mock(ExecutiveDashboardService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        featureFlags = new AiFeatureFlags();
        controller = new ExecutiveDashboardController(executiveDashboardService, authenticatedUserContext, featureFlags);
        loginAs(Set.of("MANAGER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUnavailableWhenFeatureDisabled() {
        featureFlags.setExecutiveDashboardEnabled(false);
        ResponseEntity<?> response = controller.getStrategicHealth(LocalDate.of(2026, 3, 23));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExecutiveDashboardController.ExecutiveDashboardUnavailableResponse body =
                assertInstanceOf(ExecutiveDashboardController.ExecutiveDashboardUnavailableResponse.class, response.getBody());
        assertEquals("unavailable", body.status());
        verify(executiveDashboardService, never()).getStrategicHealth(ORG_ID, LocalDate.of(2026, 3, 23));
    }

    @Test
    void returnsDashboardForManagerOrAdmin() {
        featureFlags.setExecutiveDashboardEnabled(true);
        when(executiveDashboardService.getStrategicHealth(ORG_ID, LocalDate.of(2026, 3, 23))).thenReturn(
                new ExecutiveDashboardService.ExecutiveDashboardResult(
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
                        true));

        ResponseEntity<?> response = controller.getStrategicHealth(LocalDate.of(2026, 3, 23));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExecutiveDashboardService.ExecutiveDashboardResult body =
                assertInstanceOf(ExecutiveDashboardService.ExecutiveDashboardResult.class, response.getBody());
        assertEquals(LocalDate.of(2026, 3, 23), body.weekStart());
    }

    @Test
    void returnsForbiddenForIc() {
        loginAs(Set.of("IC"));

        ResponseEntity<?> response = controller.getStrategicHealth(LocalDate.of(2026, 3, 23));

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
