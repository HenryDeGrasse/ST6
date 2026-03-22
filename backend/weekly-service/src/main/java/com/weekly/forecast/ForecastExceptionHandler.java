package com.weekly.forecast;

import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for forecast/AI controllers that reuse plan-domain exceptions.
 *
 * <p>This keeps plan-specific error envelopes consistent for planning-copilot endpoints
 * without introducing module-boundary violations in the shared exception handler.
 */
@RestControllerAdvice(basePackages = "com.weekly.forecast")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ForecastExceptionHandler {

    @ExceptionHandler(PlanAccessForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(PlanAccessForbiddenException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.FORBIDDEN, ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus()).body(response);
    }

    @ExceptionHandler(PlanValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(PlanValidationException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getErrorCode(), ex.getMessage(), ex.getDetails()
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(PlanStateException.class)
    public ResponseEntity<ApiErrorResponse> handleState(PlanStateException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getErrorCode(), ex.getMessage(),
                List.of(Map.of("planState", ex.getPlanState()))
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }
}
