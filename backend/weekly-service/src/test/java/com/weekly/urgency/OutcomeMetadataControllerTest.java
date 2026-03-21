package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link OutcomeMetadataController}.
 */
class OutcomeMetadataControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private OutcomeMetadataRepository outcomeMetadataRepository;
    private UrgencyComputeService urgencyComputeService;
    private AuthenticatedUserContext authenticatedUserContext;
    private OutcomeMetadataController controller;

    @BeforeEach
    void setUp() {
        outcomeMetadataRepository = mock(OutcomeMetadataRepository.class);
        urgencyComputeService = mock(UrgencyComputeService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new OutcomeMetadataController(
                outcomeMetadataRepository,
                urgencyComputeService,
                authenticatedUserContext
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMetadataReturnsAllRowsForAuthenticatedOrg() {
        setUpMember();
        OutcomeMetadataEntity first = metadataEntity(UUID.randomUUID());
        OutcomeMetadataEntity second = metadataEntity(UUID.randomUUID());
        when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(first, second));

        ResponseEntity<List<OutcomeMetadataResponse>> response = controller.listMetadata();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(first.getOutcomeId(), response.getBody().get(0).outcomeId());
        assertEquals(second.getOutcomeId(), response.getBody().get(1).outcomeId());
    }

    @Test
    void getMetadataReturnsNotFoundWhenOutcomeDoesNotExist() {
        setUpMember();
        when(outcomeMetadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getMetadata(OUTCOME_ID);

        assertEquals(404, response.getStatusCode().value());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("NOT_FOUND", body.error().code());
    }

    @Nested
    class UpsertMetadata {

        @Test
        void forbidsNonManagerAndNonAdminUsers() {
            setUpMember();

            ResponseEntity<?> response = controller.upsertMetadata(
                    OUTCOME_ID,
                    new OutcomeMetadataRequest(LocalDate.of(2026, 4, 30), "ACTIVITY", null, null, null, null, null)
            );

            assertEquals(403, response.getStatusCode().value());
            verify(outcomeMetadataRepository, never()).save(any());
            verify(urgencyComputeService, never()).computeUrgencyForOutcome(any(), any());
        }

        @Test
        void upsertsMetadataAndRecomputesOnlyRequestedOutcome() {
            setUpManager();
            OutcomeMetadataEntity savedEntity = metadataEntity(OUTCOME_ID);
            when(outcomeMetadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.empty());
            when(outcomeMetadataRepository.save(any(OutcomeMetadataEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(urgencyComputeService.computeUrgencyForOutcome(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.of(savedEntity));

            ResponseEntity<?> response = controller.upsertMetadata(
                    OUTCOME_ID,
                    new OutcomeMetadataRequest(
                            LocalDate.of(2026, 4, 30),
                            "METRIC",
                            "Revenue",
                            new BigDecimal("100.00"),
                            new BigDecimal("40.00"),
                            "%",
                            null
                    )
            );

            assertEquals(200, response.getStatusCode().value());
            OutcomeMetadataResponse body = assertInstanceOf(OutcomeMetadataResponse.class, response.getBody());
            assertEquals(OUTCOME_ID, body.outcomeId());
            verify(outcomeMetadataRepository).save(any(OutcomeMetadataEntity.class));
            verify(urgencyComputeService).computeUrgencyForOutcome(ORG_ID, OUTCOME_ID);
        }
    }

    @Nested
    class UpdateProgress {

        @Test
        void returnsNotFoundWhenMetadataIsMissing() {
            setUpManager();
            when(outcomeMetadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.updateProgress(
                    OUTCOME_ID,
                    new ProgressUpdateRequest(new BigDecimal("55.00"), null)
            );

            assertEquals(404, response.getStatusCode().value());
            verify(outcomeMetadataRepository, never()).save(any());
            verify(urgencyComputeService, never()).computeUrgencyForOutcome(any(), any());
        }

        @Test
        void updatesProgressFieldsAndRecomputesOnlyRequestedOutcome() {
            setUpManager();
            OutcomeMetadataEntity existing = metadataEntity(OUTCOME_ID);
            when(outcomeMetadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.of(existing));
            when(outcomeMetadataRepository.save(existing)).thenReturn(existing);
            when(urgencyComputeService.computeUrgencyForOutcome(ORG_ID, OUTCOME_ID))
                    .thenReturn(Optional.of(existing));

            ResponseEntity<?> response = controller.updateProgress(
                    OUTCOME_ID,
                    new ProgressUpdateRequest(new BigDecimal("65.50"), "[{\"status\":\"DONE\"}]")
            );

            assertEquals(200, response.getStatusCode().value());
            assertEquals(new BigDecimal("65.50"), existing.getCurrentValue());
            assertEquals("[{\"status\":\"DONE\"}]", existing.getMilestones());
            verify(outcomeMetadataRepository).save(existing);
            verify(urgencyComputeService).computeUrgencyForOutcome(ORG_ID, OUTCOME_ID);
        }
    }

    @Nested
    class RequestValidation {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @Test
        void rejectsUnknownProgressType() {
            OutcomeMetadataRequest request = new OutcomeMetadataRequest(
                    null,
                    "SCORECARD",
                    null,
                    null,
                    null,
                    null,
                    null
            );

            var violations = validator.validate(request);

            assertEquals(1, violations.size());
            assertEquals("progressType", violations.iterator().next().getPropertyPath().toString());
        }

        @Test
        void rejectsBlankMilestonesInProgressPatch() {
            ProgressUpdateRequest request = new ProgressUpdateRequest(null, "   ");

            var violations = validator.validate(request);

            assertEquals(1, violations.size());
            assertTrue(violations.iterator().next().getPropertyPath().toString().contains("milestones"));
        }
    }

    private static OutcomeMetadataEntity metadataEntity(UUID outcomeId) {
        OutcomeMetadataEntity entity = new OutcomeMetadataEntity(ORG_ID, outcomeId);
        entity.setTargetDate(LocalDate.of(2026, 4, 30));
        entity.setProgressType("ACTIVITY");
        entity.setMetricName("Revenue");
        entity.setTargetValue(new BigDecimal("100.00"));
        entity.setCurrentValue(new BigDecimal("25.00"));
        entity.setUnit("%");
        entity.setMilestones("[]");
        entity.setProgressPct(new BigDecimal("25.00"));
        entity.setUrgencyBand(UrgencyComputeService.BAND_NEEDS_ATTENTION);
        return entity;
    }

    private void setUpMember() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, Set.of()),
                        null,
                        List.of()
                )
        );
    }

    private void setUpManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()
                )
        );
    }
}
