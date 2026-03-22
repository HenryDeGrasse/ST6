package com.weekly.issues.controller;

import com.weekly.issues.service.IssueAccessDeniedException;
import com.weekly.issues.service.IssueNotFoundException;
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
 * Exception handler for issue API errors (Phase 6).
 */
@RestControllerAdvice(basePackages = "com.weekly.issues.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IssueExceptionHandler {

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

    @ExceptionHandler(IssueNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(IssueNotFoundException ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(IssueAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(IssueAccessDeniedException ex) {
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
