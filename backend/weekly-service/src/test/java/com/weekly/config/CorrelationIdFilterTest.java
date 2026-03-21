package com.weekly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Tests for {@link CorrelationIdFilter}: verifies correlation ID
 * propagation per PRD §9.7.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void propagatesExistingCorrelationId() throws Exception {
        String existingId = "test-correlation-123";
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(existingId);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, existingId);
        verify(chain).doFilter(request, response);
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME),
                org.mockito.ArgumentMatchers.argThat(arg -> arg != null && !arg.isBlank()));
        verify(chain).doFilter(request, response);
    }

    @Test
    void exposesCorrelationIdInMdcDuringRequest() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("test-id");
        doAnswer(invocation -> {
            assertEquals("test-id", MDC.get(CorrelationIdFilter.MDC_KEY));
            assertNull(MDC.get("orgId"));
            assertNull(MDC.get("userId"));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY),
                "MDC should be cleared after filter completes");
        assertNull(MDC.get("orgId"), "orgId should be cleared after filter completes");
        assertNull(MDC.get("userId"), "userId should be cleared after filter completes");
    }
}
