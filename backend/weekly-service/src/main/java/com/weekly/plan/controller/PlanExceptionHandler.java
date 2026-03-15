package com.weekly.plan.controller;

import com.weekly.plan.service.CommitNotFoundException;
import com.weekly.plan.service.OptimisticLockException;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanNotFoundException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for plan/commit API errors.
 */
@RestControllerAdvice(basePackages = "com.weekly.plan.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
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

    @ExceptionHandler(PlanAccessForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(PlanAccessForbiddenException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.FORBIDDEN, ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus()).body(response);
    }

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

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.CONFLICT, ex.getMessage(),
                List.of(Map.of(
                        "currentVersion", ex.getActualVersion(),
                        "expectedVersion", ex.getExpectedVersion()
                ))
        );
        return ResponseEntity.status(409).body(response);
    }
}
