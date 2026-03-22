package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.forecast.ForecastDtos.OutcomeForecastListResponse;
import com.weekly.forecast.ForecastDtos.OutcomeForecastResponse;
import com.weekly.shared.ApiErrorResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * Unit tests for {@link ForecastController}.
 */
class ForecastControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private TargetDateForecastService targetDateForecastService;
    private AuthenticatedUserContext authenticatedUserContext;
    private ForecastController controller;

    @BeforeEach
    void setUp() {
        targetDateForecastService = mock(TargetDateForecastService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new ForecastController(targetDateForecastService, authenticatedUserContext);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOutcomeForecastsReturnsWrappedForecastList() {
        when(targetDateForecastService.getOrComputeOrgForecasts(ORG_ID)).thenReturn(List.of(sampleForecast()));

        ResponseEntity<OutcomeForecastListResponse> response = controller.getOutcomeForecasts();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        OutcomeForecastListResponse body = assertInstanceOf(OutcomeForecastListResponse.class, response.getBody());
        assertEquals(1, body.forecasts().size());
        assertEquals(OUTCOME_ID.toString(), body.forecasts().getFirst().outcomeId());
    }

    @Test
    void getOutcomeForecastReturnsNotFoundWhenNoForecastExists() {
        when(targetDateForecastService.getOrComputeOutcomeForecast(ORG_ID, OUTCOME_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getOutcomeForecast(OUTCOME_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("NOT_FOUND", body.error().code());
    }

    private OutcomeForecastResponse sampleForecast() {
        return new OutcomeForecastResponse(
                OUTCOME_ID.toString(),
                "Improve activation",
                LocalDate.of(2026, 4, 12),
                LocalDate.of(2026, 4, 18),
                new BigDecimal("72.50"),
                new BigDecimal("5.2500"),
                new BigDecimal("0.7600"),
                "HIGH",
                TargetDateForecastService.STATUS_NEEDS_ATTENTION,
                TargetDateForecastService.MODEL_VERSION,
                List.of(),
                List.of("Add more mapped work"),
                "2026-03-21T10:15:30Z");
    }
}
