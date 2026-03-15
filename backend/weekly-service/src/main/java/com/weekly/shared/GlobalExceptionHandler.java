package com.weekly.shared;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler that provides consistent {@link ApiErrorResponse} envelopes for
 * common infrastructure-level exceptions not covered by
 * {@link com.weekly.plan.controller.PlanExceptionHandler}.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — Bean Validation failures (fallback for
 *       controllers outside {@code com.weekly.plan.controller})</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed or unreadable JSON body</li>
 *   <li>{@link MissingRequestHeaderException} — missing required headers (e.g.,
 *       {@code If-Match}, {@code X-User-Id}, {@code X-Org-Id})</li>
 *   <li>{@link DateTimeParseException} — invalid date format in path variables
 *       (e.g., {@code weekStart})</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — type conversion failure for path /
 *       query parameters (e.g., non-UUID {@code planId})</li>
 *   <li>{@link IllegalArgumentException} — explicit invalid arguments thrown in controller
 *       logic (e.g., {@code UUID.fromString} on unvalidated input)</li>
 *   <li>{@link Exception} catch-all — prevents stack-trace leakage for unexpected errors</li>
 * </ul>
 *
 * <p>The more-specific {@link com.weekly.plan.controller.PlanExceptionHandler} (scoped to
 * {@code com.weekly.plan.controller}) takes precedence over this handler for plan-controller
 * exceptions it already handles. This advice is explicitly ordered last so the plan-specific
 * advice can resolve its domain exceptions first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation failures ({@code @Valid} on request bodies).
     * Acts as a fallback for controllers outside {@code com.weekly.plan.controller};
     * the higher-priority scoped {@link com.weekly.plan.controller.PlanExceptionHandler}
     * takes precedence there.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
                ))
                .collect(Collectors.toList());
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR, "Request validation failed", details
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
    }

    /**
     * Malformed or syntactically invalid JSON request body.
     * Returns 400 with {@link ErrorCode#VALIDATION_ERROR} and a human-readable message
     * identifying the offending field when possible.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = "Malformed or unreadable request body";
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            message = "Invalid value for field '" + ife.getPath().get(0).getFieldName() + "'";
        }
        ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, message);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Required HTTP header is absent (e.g., {@code If-Match} on lifecycle mutations,
     * {@code X-User-Id} / {@code X-Org-Id} on dev-auth endpoints).
     * Returns 400 with {@link ErrorCode#VALIDATION_ERROR} and the missing header name.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Required header missing: " + ex.getHeaderName(),
                List.of(Map.of("header", ex.getHeaderName()))
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Invalid date string in a path variable (e.g., {@code weekStart} is not a valid
     * ISO-8601 date).  Returns 422 with {@link ErrorCode#INVALID_WEEK_START}.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiErrorResponse> handleDateTimeParse(DateTimeParseException ex) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INVALID_WEEK_START,
                "Invalid date format: '" + ex.getParsedString() + "'"
        );
        return ResponseEntity.status(ErrorCode.INVALID_WEEK_START.getHttpStatus()).body(body);
    }

    /**
     * Type conversion failure for a path or query parameter (e.g., a non-UUID value
     * passed for {@code planId}).  Returns 422 with {@link ErrorCode#VALIDATION_ERROR}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        String message;
        if (UUID.class.equals(ex.getRequiredType())) {
            message = "Invalid UUID format for parameter '" + ex.getName()
                    + "': '" + ex.getValue() + "'";
        } else {
            message = "Invalid value '" + ex.getValue()
                    + "' for parameter '" + ex.getName() + "'";
        }
        ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, message);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
    }

    /**
     * Explicit {@link IllegalArgumentException} thrown in controller or service logic
     * (e.g., {@link UUID#fromString} on unvalidated string input).
     * Returns 422 with {@link ErrorCode#VALIDATION_ERROR}.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument"
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
    }

    /**
     * Catch-all for any unhandled exception. Logs the full stack trace server-side
     * but returns only a generic 500 envelope to the client, preventing stack-trace leakage.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        LOG.error("Unhandled exception", ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(body);
    }
}
