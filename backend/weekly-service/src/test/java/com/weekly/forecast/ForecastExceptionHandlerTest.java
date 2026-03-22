package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ForecastExceptionHandler}.
 */
class ForecastExceptionHandlerTest {

    private final ForecastExceptionHandler handler = new ForecastExceptionHandler();

    @Test
    void handleForbiddenReturns403Envelope() {
        var response = handler.handleForbidden(new PlanAccessForbiddenException("Manager role required"));

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("FORBIDDEN", response.getBody().error().code());
        assertEquals("Manager role required", response.getBody().error().message());
    }

    @Test
    void handleValidationReturnsConfiguredErrorEnvelope() {
        var response = handler.handleValidation(new PlanValidationException(
                ErrorCode.MISSING_RCDO_OR_REASON,
                "outcomeId must be a UUID string",
                List.of(Map.of("field", "outcomeId"))));

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MISSING_RCDO_OR_REASON", response.getBody().error().code());
        assertEquals("outcomeId must be a UUID string", response.getBody().error().message());
        assertEquals("outcomeId", response.getBody().error().details().getFirst().get("field"));
    }

    @Test
    void handleStateReturnsPlanStateDetails() {
        var response = handler.handleState(new PlanStateException(
                ErrorCode.PLAN_NOT_IN_DRAFT,
                "Managers may only apply AI suggestions to DRAFT plans",
                "LOCKED"));

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PLAN_NOT_IN_DRAFT", response.getBody().error().code());
        assertEquals("LOCKED", response.getBody().error().details().getFirst().get("planState"));
    }

    @Test
    void handleValidationSupportsInvalidWeekStartDetails() {
        var response = handler.handleValidation(new PlanValidationException(
                ErrorCode.INVALID_WEEK_START,
                "weekStart must be an ISO-8601 date",
                List.of(Map.of("field", "weekStart", "provided", "03/23/2026"))));

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_WEEK_START", response.getBody().error().code());
        assertEquals("03/23/2026", response.getBody().error().details().getFirst().get("provided"));
    }
}
