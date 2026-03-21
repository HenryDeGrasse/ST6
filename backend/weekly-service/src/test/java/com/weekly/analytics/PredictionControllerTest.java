package com.weekly.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.weekly.analytics.dto.Prediction;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link PredictionController}: authorization rules and service delegation.
 */
class PredictionControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SELF_USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");

    private TestPredictionService predictionService;
    private PredictionController controller;

    @BeforeEach
    void setUp() {
        predictionService = new TestPredictionService();
        controller = new PredictionController(predictionService, new AuthenticatedUserContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsManagersToRequestPredictionsForAnyUser() {
        login(MANAGER_ID, Set.of("MANAGER"));
        List<Prediction> stub = List.of(new Prediction(
                PredictionService.TYPE_CARRY_FORWARD,
                true,
                PredictionService.CONFIDENCE_HIGH,
                "carry-forward risk"
        ));
        predictionService.response = stub;

        ResponseEntity<?> response = controller.getUserPredictions(OTHER_USER_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(stub, response.getBody());
        assertEquals(ORG_ID, predictionService.lastOrgId);
        assertEquals(OTHER_USER_ID, predictionService.lastUserId);
    }

    @Test
    void allowsSelfServiceForNonManagers() {
        login(SELF_USER_ID, Set.of("INDIVIDUAL_CONTRIBUTOR"));
        List<Prediction> stub = List.of(new Prediction(
                PredictionService.TYPE_LATE_LOCK,
                true,
                PredictionService.CONFIDENCE_HIGH,
                "late-lock risk"
        ));
        predictionService.response = stub;

        ResponseEntity<?> response = controller.getUserPredictions(SELF_USER_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(stub, response.getBody());
        assertEquals(ORG_ID, predictionService.lastOrgId);
        assertEquals(SELF_USER_ID, predictionService.lastUserId);
    }

    @Test
    void returnsForbiddenForNonManagerRequestingAnotherUsersPredictions() {
        login(SELF_USER_ID, Set.of("INDIVIDUAL_CONTRIBUTOR"));

        ResponseEntity<?> response = controller.getUserPredictions(OTHER_USER_ID);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiErrorResponse error = (ApiErrorResponse) response.getBody();
        assertNotNull(error);
        assertEquals("FORBIDDEN", error.error().code());
        assertNull(predictionService.lastOrgId);
        assertNull(predictionService.lastUserId);
    }

    private void login(UUID userId, Set<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(userId, ORG_ID, roles),
                        null,
                        List.of()
                )
        );
    }

    private static final class TestPredictionService extends PredictionService {

        private UUID lastOrgId;
        private UUID lastUserId;
        private List<Prediction> response = List.of();

        private TestPredictionService() {
            super(new JdbcTemplate());
        }

        @Override
        public List<Prediction> getUserPredictions(UUID orgId, UUID userId) {
            lastOrgId = orgId;
            lastUserId = userId;
            return response;
        }
    }
}
