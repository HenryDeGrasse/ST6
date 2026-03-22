package com.weekly.team.controller;

import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import com.weekly.team.service.TeamAccessDeniedException;
import com.weekly.team.service.TeamAccessRequestNotFoundException;
import com.weekly.team.service.TeamNotFoundException;
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
 * Exception handler for team API errors (Phase 6).
 */
@RestControllerAdvice(basePackages = "com.weekly.team.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TeamExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
                ))
                .collect(Collectors.toList());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Request validation failed", details));
    }

    @ExceptionHandler({TeamNotFoundException.class, TeamAccessRequestNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(TeamAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(TeamAccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }
}
