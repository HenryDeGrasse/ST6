package com.weekly.shared;

import java.util.List;
import java.util.Map;

/**
 * Standard API error envelope.
 * { "error": { "code": "...", "message": "...", "details": [...] } }
 */
public record ApiErrorResponse(ApiError error) {

    public record ApiError(
            String code,
            String message,
            List<Map<String, Object>> details
    ) {}

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(new ApiError(code.name(), message, List.of()));
    }

    public static ApiErrorResponse of(ErrorCode code, String message, List<Map<String, Object>> details) {
        return new ApiErrorResponse(new ApiError(code.name(), message, details));
    }
}
