package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.rcdo.RcdoClient;
import com.weekly.shared.SlackInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StrategicSlackService}.
 *
 * <p>Follows {@code UrgencyComputeServiceTest} patterns: Mockito mocks,
 * {@code @BeforeEach} setup, and {@code @Nested} test classes grouped by behaviour.
 *
 * <p>Mocks: {@link OutcomeMetadataRepository}, {@link RcdoClient}.
 *
 * <p>All test cases drive {@link StrategicSlackService#computeStrategicSlack} with a
 * stubbed list of {@link OutcomeMetadataEntity} objects whose {@code urgencyBand} is
 * already pre-set, so the tests exercise the floor accumulation and band mapping
 * independently of the {@link UrgencyComputeService}.
 */
class StrategicSlackServiceTest {

    private static final UUID ORG_ID     = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    private OutcomeMetadataRepository outcomeMetadataRepository;
    private StrategicSlackService service;

    @BeforeEach
    void setUp() {
        outcomeMetadataRepository = mock(OutcomeMetadataRepository.class);
        RcdoClient rcdoClient = mock(RcdoClient.class);
        service = new StrategicSlackService(outcomeMetadataRepository, rcdoClient);
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * Creates a {@link OutcomeMetadataEntity} for {@code ORG_ID} with the given
     * urgency band already set (simulating post-compute state).
     */
    private OutcomeMetadataEntity outcomeWithBand(String band) {
        OutcomeMetadataEntity entity = new OutcomeMetadataEntity(ORG_ID, UUID.randomUUID());
        entity.setUrgencyBand(band);
        return entity;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1–8: Slack band and strategic focus floor computation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class SlackBandAndFloor {

        /**
         * Test 1: All ON_TRACK outcomes → no urgency penalties → floor stays at base
         * (0.50) → HIGH_SLACK.
         */
        @Test
        void allOnTrackOutcomesGiveHighSlackAtBaseFloor() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_ON_TRACK),
                    outcomeWithBand(UrgencyComputeService.BAND_ON_TRACK),
                    outcomeWithBand(UrgencyComputeService.BAND_ON_TRACK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_HIGH_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.50"), info.strategicFocusFloor());
        }

        /**
         * Test 2: One NEEDS_ATTENTION outcome → floor = 0.50 + 0.05 = 0.55.
         *
         * <p>The spec bands say HIGH_SLACK covers floor ≤ 0.55 (inclusive), so 0.55
         * maps to HIGH_SLACK. The NEEDS_ATTENTION penalty raises the floor to the
         * high-slack boundary but does not cross into MODERATE_SLACK territory.
         */
        @Test
        void oneNeedsAttentionOutcomeRaisesFloorTo055StaysHighSlack() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_HIGH_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.55"), info.strategicFocusFloor());
        }

        /**
         * Test 3: One AT_RISK outcome → floor = 0.50 + 0.10 = 0.60 → MODERATE_SLACK.
         *
         * <p>0.60 is above 0.55 (HIGH_SLACK ceiling) and at most 0.70 → MODERATE_SLACK.
         */
        @Test
        void oneAtRiskOutcomeGivesModerateSlackFloor060() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_MODERATE_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.60"), info.strategicFocusFloor());
        }

        /**
         * Test 4: One CRITICAL outcome → floor = 0.50 + 0.15 = 0.65 → MODERATE_SLACK.
         *
         * <p>0.65 is above 0.55 and at most 0.70 → MODERATE_SLACK.
         */
        @Test
        void oneCriticalOutcomeGivesModerateSlackFloor065() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_MODERATE_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.65"), info.strategicFocusFloor());
        }

        /**
         * Test 5: 1 CRITICAL + 2 AT_RISK → floor = 0.50 + 0.15 + 0.10 + 0.10 = 0.85
         * → LOW_SLACK.
         *
         * <p>0.85 is the exact upper boundary of LOW_SLACK (floor ≤ 0.85).
         */
        @Test
        void mixedOneCriticalTwoAtRiskGivesLowSlackFloor085() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK),
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_LOW_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.85"), info.strategicFocusFloor());
        }

        /**
         * Test 6: Four CRITICAL outcomes → uncapped floor = 0.50 + 4×0.15 = 1.10,
         * capped at 0.95 → NO_SLACK.
         *
         * <p>Exercises the {@link StrategicSlackService#FLOOR_CAP} guard: the raw
         * accumulation would exceed 1.0, but {@code Math.min(floor, 0.95)} clamps
         * the result.
         */
        @Test
        void manyCriticalOutcomesCapFloorAt095GivingNoSlack() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL) // pushes uncapped to 1.10
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_NO_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.95"), info.strategicFocusFloor());
        }

        /**
         * Test 7: No outcomes at all → floor stays at base 0.50 → HIGH_SLACK.
         */
        @Test
        void noOutcomesGivesHighSlackAtBaseFloor() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_HIGH_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.50"), info.strategicFocusFloor());
        }

        /**
         * Test 8: All outcomes are NO_TARGET → no urgency penalties → floor = 0.50
         * → HIGH_SLACK.
         *
         * <p>NO_TARGET outcomes are in the {@code default} branch of the switch, so
         * they do not contribute to the floor.
         */
        @Test
        void allNoTargetOutcomesGiveHighSlackAtBaseFloor() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_NO_TARGET),
                    outcomeWithBand(UrgencyComputeService.BAND_NO_TARGET),
                    outcomeWithBand(UrgencyComputeService.BAND_NO_TARGET)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_HIGH_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.50"), info.strategicFocusFloor());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. atRiskCount and criticalCount reporting
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class RiskCounts {

        /**
         * 9a. AT_RISK outcomes are counted in {@code atRiskCount} only.
         *
         * <p>One ON_TRACK outcome should not appear in either count.
         */
        @Test
        void atRiskCountReflectsOnlyAtRiskOutcomes() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK),
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK),
                    outcomeWithBand(UrgencyComputeService.BAND_ON_TRACK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(2, info.atRiskCount());
            assertEquals(0, info.criticalCount());
        }

        /**
         * 9b. CRITICAL outcomes are counted in {@code criticalCount} only.
         *
         * <p>One AT_RISK outcome is also present to confirm it does not affect
         * {@code criticalCount}.
         */
        @Test
        void criticalCountReflectsOnlyCriticalOutcomes() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(3, info.criticalCount());
            assertEquals(1, info.atRiskCount());
        }

        /**
         * 9c. NEEDS_ATTENTION outcomes raise the floor but are NOT counted in either
         * {@code atRiskCount} or {@code criticalCount}.
         */
        @Test
        void needsAttentionNotIncludedInAtRiskOrCriticalCount() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION),
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(0, info.atRiskCount());
            assertEquals(0, info.criticalCount());
        }

        /**
         * 9d. Mixed portfolio correctly reports all counts simultaneously.
         *
         * <p>Portfolio: 1 CRITICAL, 1 AT_RISK, 1 NEEDS_ATTENTION, 1 ON_TRACK.
         * Expected: criticalCount=1, atRiskCount=1.
         */
        @Test
        void mixedPortfolioCorrectlyReportsAllCounts() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_AT_RISK),
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION),
                    outcomeWithBand(UrgencyComputeService.BAND_ON_TRACK)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(1, info.criticalCount());
            assertEquals(1, info.atRiskCount());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        /**
         * Outcomes with a {@code null} urgency band are silently skipped — they do
         * not contribute to the floor, atRiskCount, or criticalCount.
         */
        @Test
        void nullUrgencyBandOutcomesAreSkipped() {
            // Entity with no urgencyBand set → getUrgencyBand() returns null
            OutcomeMetadataEntity nullBandEntity =
                    new OutcomeMetadataEntity(ORG_ID, UUID.randomUUID());
            when(outcomeMetadataRepository.findByOrgId(ORG_ID))
                    .thenReturn(List.of(nullBandEntity));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_HIGH_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.50"), info.strategicFocusFloor());
            assertEquals(0, info.atRiskCount());
            assertEquals(0, info.criticalCount());
        }

        /**
         * Three CRITICAL outcomes reach exactly 0.95 (0.50 + 3×0.15 = 0.95) which
         * is the cap value. Verifies that the cap is inclusive: floor == 0.95 maps to
         * NO_SLACK.
         */
        @Test
        void floorExactlyAtCapMapsToNoSlack() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL),
                    outcomeWithBand(UrgencyComputeService.BAND_CRITICAL)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_NO_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.95"), info.strategicFocusFloor());
        }

        /**
         * Two NEEDS_ATTENTION outcomes → floor = 0.50 + 0.10 = 0.60 → MODERATE_SLACK.
         *
         * <p>Confirms that multiple NEEDS_ATTENTION outcomes accumulate correctly and
         * cross into MODERATE_SLACK when the combined penalty exceeds 0.05.
         */
        @Test
        void twoNeedsAttentionOutcomesGivesModerateSlack() {
            when(outcomeMetadataRepository.findByOrgId(ORG_ID)).thenReturn(List.of(
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION),
                    outcomeWithBand(UrgencyComputeService.BAND_NEEDS_ATTENTION)
            ));

            SlackInfo info = service.computeStrategicSlack(ORG_ID, MANAGER_ID);

            assertEquals(StrategicSlackService.BAND_MODERATE_SLACK, info.slackBand());
            assertEquals(new BigDecimal("0.60"), info.strategicFocusFloor());
        }
    }
}
