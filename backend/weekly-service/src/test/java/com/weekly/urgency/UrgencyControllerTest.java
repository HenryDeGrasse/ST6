package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link UrgencyController}.
 */
class UrgencyControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private UrgencyDataProvider urgencyDataProvider;
    private StrategicSlackService strategicSlackService;
    private AuthenticatedUserContext authenticatedUserContext;
    private UrgencyController controller;

    @BeforeEach
    void setUp() {
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        strategicSlackService = mock(StrategicSlackService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new UrgencyController(
                urgencyDataProvider,
                strategicSlackService,
                authenticatedUserContext
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUrgencySummaryReturnsWrappedOrgOutcomes() {
        List<UrgencyInfo> outcomes = List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Improve onboarding conversion",
                LocalDate.of(2026, 4, 30),
                new BigDecimal("42.50"),
                new BigDecimal("55.00"),
                UrgencyComputeService.BAND_AT_RISK,
                41L
        ));
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(outcomes);

        ResponseEntity<Map<String, Object>> response = controller.getUrgencySummary();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = assertInstanceOf(Map.class, response.getBody());
        assertNotNull(body);
        assertEquals(outcomes, body.get("outcomes"));
        verify(urgencyDataProvider).getOrgUrgencySummary(ORG_ID);
        verifyNoInteractions(strategicSlackService);
    }

    @Test
    void getStrategicSlackReturnsWrappedSlackForAuthenticatedUser() {
        SlackInfo slackInfo = new SlackInfo(
                StrategicSlackService.BAND_LOW_SLACK,
                new BigDecimal("0.75"),
                2,
                1
        );
        when(strategicSlackService.computeStrategicSlack(ORG_ID, USER_ID)).thenReturn(slackInfo);

        ResponseEntity<Map<String, Object>> response = controller.getStrategicSlack();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = assertInstanceOf(Map.class, response.getBody());
        assertNotNull(body);
        assertEquals(slackInfo, body.get("slack"));
        verify(strategicSlackService).computeStrategicSlack(ORG_ID, USER_ID);
        verifyNoInteractions(urgencyDataProvider);
    }
}
