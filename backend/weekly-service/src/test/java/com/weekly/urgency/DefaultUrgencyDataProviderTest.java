package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link DefaultUrgencyDataProvider}.
 */
class DefaultUrgencyDataProviderTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OUTCOME_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-20T12:00:00Z"), ZoneOffset.UTC);

    private OutcomeMetadataRepository metadataRepository;
    private StrategicSlackService strategicSlackService;
    private RcdoClient rcdoClient;
    private DefaultUrgencyDataProvider provider;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(OutcomeMetadataRepository.class);
        strategicSlackService = mock(StrategicSlackService.class);
        rcdoClient = mock(RcdoClient.class);
        provider = new DefaultUrgencyDataProvider(
                metadataRepository,
                strategicSlackService,
                rcdoClient,
                FIXED_CLOCK
        );
    }

    @Test
    void getOutcomeUrgencyReturnsNullWhenMetadataDoesNotExist() {
        when(metadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                .thenReturn(Optional.empty());

        UrgencyInfo urgencyInfo = provider.getOutcomeUrgency(ORG_ID, OUTCOME_ID);

        assertNull(urgencyInfo);
    }

    @Test
    void getOutcomeUrgencyBuildsExpectedUrgencyInfo() {
        OutcomeMetadataEntity metadata = metadata(LocalDate.of(2026, 3, 30));
        metadata.setProgressPct(new BigDecimal("42.50"));
        metadata.setUrgencyBand(UrgencyComputeService.BAND_AT_RISK);

        when(metadataRepository.findByOrgIdAndOutcomeId(ORG_ID, OUTCOME_ID))
                .thenReturn(Optional.of(metadata));
        when(rcdoClient.getOutcome(ORG_ID, OUTCOME_ID))
                .thenReturn(Optional.of(new RcdoOutcomeDetail(
                        OUTCOME_ID.toString(),
                        "Launch new self-serve funnel",
                        UUID.randomUUID().toString(),
                        "Improve activation",
                        UUID.randomUUID().toString(),
                        "Revenue growth"
                )));

        UrgencyInfo urgencyInfo = provider.getOutcomeUrgency(ORG_ID, OUTCOME_ID);

        assertNotNull(urgencyInfo);
        assertEquals(OUTCOME_ID, urgencyInfo.outcomeId());
        assertEquals("Launch new self-serve funnel", urgencyInfo.outcomeName());
        assertEquals(LocalDate.of(2026, 3, 30), urgencyInfo.targetDate());
        assertEquals(new BigDecimal("42.50"), urgencyInfo.progressPct());
        assertEquals(new BigDecimal("50.00"), urgencyInfo.expectedProgressPct());
        assertEquals(UrgencyComputeService.BAND_AT_RISK, urgencyInfo.urgencyBand());
        assertEquals(10L, urgencyInfo.daysRemaining());
    }

    @Test
    void getOrgUrgencySummaryDefaultsMissingUrgencyBandAndMissingTargetDate() {
        OutcomeMetadataEntity metadata = metadata(null);
        metadata.setProgressPct(new BigDecimal("15.00"));

        when(metadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(metadata));
        when(rcdoClient.getOutcome(ORG_ID, OUTCOME_ID)).thenReturn(Optional.empty());

        List<UrgencyInfo> summary = provider.getOrgUrgencySummary(ORG_ID);

        assertEquals(1, summary.size());
        UrgencyInfo urgencyInfo = summary.get(0);
        assertNull(urgencyInfo.outcomeName());
        assertNull(urgencyInfo.targetDate());
        assertEquals(new BigDecimal("15.00"), urgencyInfo.progressPct());
        assertNull(urgencyInfo.expectedProgressPct());
        assertEquals(UrgencyComputeService.BAND_NO_TARGET, urgencyInfo.urgencyBand());
        assertEquals(Long.MIN_VALUE, urgencyInfo.daysRemaining());
    }

    @Test
    void getStrategicSlackDelegatesToStrategicSlackService() {
        SlackInfo slackInfo = new SlackInfo("MODERATE_SLACK", new BigDecimal("0.65"), 2, 1);
        when(strategicSlackService.computeStrategicSlack(ORG_ID, MANAGER_ID)).thenReturn(slackInfo);

        SlackInfo result = provider.getStrategicSlack(ORG_ID, MANAGER_ID);

        assertEquals(slackInfo, result);
        verify(strategicSlackService).computeStrategicSlack(ORG_ID, MANAGER_ID);
    }

    private OutcomeMetadataEntity metadata(LocalDate targetDate) {
        OutcomeMetadataEntity metadata = new OutcomeMetadataEntity(ORG_ID, OUTCOME_ID);
        ReflectionTestUtils.setField(metadata, "createdAt", Instant.parse("2026-03-10T00:00:00Z"));
        if (targetDate != null) {
            metadata.setTargetDate(targetDate);
        }
        return metadata;
    }
}
