package com.weekly.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet filter enforcing idempotency for lifecycle mutation endpoints.
 *
 * <p>Intercepts {@code POST} requests to the four lifecycle endpoints:
 * <ul>
 *   <li>{@code /api/v1/plans/{planId}/lock}</li>
 *   <li>{@code /api/v1/plans/{planId}/start-reconciliation}</li>
 *   <li>{@code /api/v1/plans/{planId}/submit-reconciliation}</li>
 *   <li>{@code /api/v1/plans/{planId}/carry-forward}</li>
 * </ul>
 *
 * <p>For each such request:
 * <ol>
 *   <li>Reads the {@code Idempotency-Key} header (UUID); returns 400 if absent/invalid.</li>
 *   <li>Checks the {@code idempotency_keys} table for a prior record matching
 *       {@code (orgId, idempotencyKey)}.</li>
 *   <li>If found with the same request hash (method + path + query + body) — replays the
 *       cached response (idempotent replay).</li>
 *   <li>If found with a different request hash — returns 422
 *       {@link ErrorCode#IDEMPOTENCY_KEY_REUSE}.</li>
 *   <li>If not found — lets the request proceed, captures the response, and stores it.</li>
 * </ol>
 *
 * <p>This filter is registered inside the Spring Security filter chain (after JWT
 * authentication) via {@link com.weekly.config.SecurityConfiguration}, so that
 * {@link SecurityContextHolder} already holds the authenticated principal when
 * this filter executes.
 */
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> IDEMPOTENCY_PATTERNS = List.of(
            "/api/v1/plans/*/lock",
            "/api/v1/plans/*/start-reconciliation",
            "/api/v1/plans/*/submit-reconciliation",
            "/api/v1/plans/*/carry-forward"
    );

    private final IdempotencyKeyService idempotencyKeyService;
    private final ObjectMapper objectMapper;

    public IdempotencyKeyFilter(
            IdempotencyKeyService idempotencyKeyService,
            ObjectMapper objectMapper
    ) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equals(method) || !requiresIdempotencyKey(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Resolve principal (already set by JwtAuthenticationFilter)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            // Authentication missing — let the security layer handle the 401
            filterChain.doFilter(request, response);
            return;
        }

        // Read the raw request body up front so we can hash it and replay it downstream
        byte[] requestBodyBytes = request.getInputStream().readAllBytes();
        BodyReplayRequestWrapper replayableRequest =
                new BodyReplayRequestWrapper(request, requestBodyBytes);

        // Validate the Idempotency-Key header
        String idempotencyKeyHeader = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            writeErrorResponse(response, ErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "Idempotency-Key header is required for lifecycle mutation endpoints");
            return;
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException ex) {
            writeErrorResponse(response, ErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "Idempotency-Key header must be a valid UUID");
            return;
        }

        UUID orgId = principal.orgId();
        UUID userId = principal.userId();
        String requestHash = requestHash(method, path, request.getQueryString(), requestBodyBytes);

        // Check for an existing stored response
        Optional<IdempotencyKeyEntity> existing =
                idempotencyKeyService.findExisting(orgId, idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyKeyEntity stored = existing.get();
            if (stored.getRequestHash().equals(requestHash)) {
                // Same key + same request fingerprint → replay cached response
                LOG.debug("Replaying idempotent response for key={} org={}", idempotencyKey, orgId);
                replayResponse(response, stored);
            } else {
                // Same key + different request fingerprint → conflict
                LOG.warn("Idempotency key reuse detected: key={} org={}", idempotencyKey, orgId);
                writeErrorResponse(response, ErrorCode.IDEMPOTENCY_KEY_REUSE,
                        "Idempotency-Key has already been used with a different request");
            }
            return;
        }

        // No prior record — proceed with the request and capture the response
        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);

        boolean requestCompleted = false;
        try {
            filterChain.doFilter(replayableRequest, responseWrapper);
            requestCompleted = true;
        } finally {
            if (requestCompleted) {
                byte[] responseBodyBytes = responseWrapper.getContentAsByteArray();
                int status = responseWrapper.getStatus();

                // Store the response for future replay (best-effort; never block the caller)
                tryStoreResponse(orgId, idempotencyKey, userId, path, requestHash,
                        status, responseBodyBytes);

                // Flush the buffered response body to the actual response
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean requiresIdempotencyKey(String path) {
        for (String pattern : IDEMPOTENCY_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        String body = String.format(
                "{\"error\":{\"code\":\"%s\",\"message\":\"%s\",\"details\":[]}}",
                errorCode.name(), message
        );
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private void replayResponse(
            HttpServletResponse response,
            IdempotencyKeyEntity stored
    ) throws IOException {
        String body = objectMapper.writeValueAsString(stored.getResponseBody());
        response.setStatus(stored.getResponseStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("X-Idempotency-Replay", "true");
        response.getWriter().write(body);
    }

    private void tryStoreResponse(
            UUID orgId,
            UUID idempotencyKey,
            UUID userId,
            String endpoint,
            String requestHash,
            int responseStatus,
            byte[] responseBodyBytes
    ) {
        try {
            Map<String, Object> responseBody = parseResponseBody(responseBodyBytes);
            idempotencyKeyService.store(
                    orgId, idempotencyKey, userId, endpoint,
                    requestHash, responseStatus, responseBody
            );
        } catch (Exception ex) {
            // Never let idempotency storage failures propagate to the caller
            LOG.error("Failed to store idempotency response for key={} org={}: {}",
                    idempotencyKey, orgId, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponseBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(bytes, new TypeReference<Object>() { });
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            return Map.of("body", parsed);
        } catch (Exception ex) {
            LOG.warn("Response body is not valid JSON; storing empty map");
            return Map.of();
        }
    }

    private static String requestHash(
            String method,
            String path,
            String query,
            byte[] requestBodyBytes
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, method);
            updateDigest(digest, "\n");
            updateDigest(digest, path);
            updateDigest(digest, "\n");
            updateDigest(digest, query == null ? "" : query);
            updateDigest(digest, "\n");
            digest.update(requestBodyBytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    // ── Inner: request wrapper that replays a cached body ─────────────────────

    /**
     * HttpServletRequestWrapper that serves a pre-read body from memory so that
     * the request body can be consumed multiple times (hash + downstream read).
     */
    static class BodyReplayRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        BodyReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public int read(byte[] buf, int off, int len) {
                    return bais.read(buf, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)
            );
        }

        byte[] getBodyBytes() {
            return body;
        }
    }
}
