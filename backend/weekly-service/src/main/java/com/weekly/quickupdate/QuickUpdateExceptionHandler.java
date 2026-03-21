package com.weekly.quickupdate;

import com.weekly.plan.service.CommitNotFoundException;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanNotFoundException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler scoped to the {@code com.weekly.quickupdate} package.
 *
 * <p>The existing {@link com.weekly.plan.controller.PlanExceptionHandler} is
 * scoped to {@code basePackages="com.weekly.plan.controller"} and therefore
 * does NOT cover exceptions thrown from controllers in this package. Without
 * this handler, {@link PlanNotFoundException}, {@link CommitNotFoundException},
 * {@link PlanStateException}, and {@link PlanAccessForbiddenException} would
 * fall through to the global catch-all and return incorrect 500 responses.
 */
@RestControllerAdvice(basePackages = "com.weekly.quickupdate")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QuickUpdateExceptionHandler {

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PlanNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus()).body(response);
    }

    @ExceptionHandler(CommitNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(CommitNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus()).body(response);
    }

    @ExceptionHandler(PlanStateException.class)
    public ResponseEntity<ApiErrorResponse> handleState(PlanStateException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getErrorCode(), ex.getMessage(),
                List.of(Map.of("planState", ex.getPlanState()))
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
                ))
                .collect(Collectors.toList());
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                details
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }
}
