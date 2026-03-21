package com.weekly.urgency;

import com.weekly.rcdo.RcdoClient;
import com.weekly.shared.SlackInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes strategic slack for an organisation's RCDO outcome portfolio.
 *
 * <p>Strategic slack is the inverse of urgency pressure — the higher the
 * {@code strategicFocusFloor} value the less slack the organisation has.
 *
 * <p>Algorithm (phase-3 spec §7):
 * <ul>
 *   <li>Base floor = 0.50</li>
 *   <li>Each {@code CRITICAL} outcome adds +0.15 to the floor.</li>
 *   <li>Each {@code AT_RISK} outcome adds +0.10 to the floor.</li>
 *   <li>Each {@code NEEDS_ATTENTION} outcome adds +0.05 to the floor.</li>
 *   <li>Floor is capped at 0.95.</li>
 * </ul>
 *
 * <p>The floor is then mapped to a slack band:
 * <ul>
 *   <li>{@code HIGH_SLACK}     — floor &le; 0.55</li>
 *   <li>{@code MODERATE_SLACK} — 0.56 &le; floor &le; 0.70</li>
 *   <li>{@code LOW_SLACK}      — 0.71 &le; floor &le; 0.85</li>
 *   <li>{@code NO_SLACK}       — 0.86 &le; floor &le; 0.95</li>
 * </ul>
 *
 * <p>Urgency is org-wide per spec §7: "Multiple teams contribute to one
 * outcome → progress is org-wide". The {@code managerId} parameter is
 * reserved for future per-team scoping (Phase 4).
 */
@Service
public class StrategicSlackService {

    /** Base strategic focus floor before any urgency penalties are applied. */
    static final double FLOOR_BASE = 0.50;

    /** Maximum strategic focus floor (hard cap). */
    static final double FLOOR_CAP = 0.95;

    /** Floor increment for each {@code CRITICAL} outcome. */
    static final double CRITICAL_INCREMENT = 0.15;

    /** Floor increment for each {@code AT_RISK} outcome. */
    static final double AT_RISK_INCREMENT = 0.10;

    /** Floor increment for each {@code NEEDS_ATTENTION} outcome. */
    static final double NEEDS_ATTENTION_INCREMENT = 0.05;

    // ── Slack band boundaries ─────────────────────────────────────────────────

    /** Upper bound (inclusive) for the {@code HIGH_SLACK} band. */
    static final double HIGH_SLACK_THRESHOLD = 0.55;

    /** Upper bound (inclusive) for the {@code MODERATE_SLACK} band. */
    static final double MODERATE_SLACK_THRESHOLD = 0.70;

    /** Upper bound (inclusive) for the {@code LOW_SLACK} band. */
    static final double LOW_SLACK_THRESHOLD = 0.85;

    // ── Slack band name constants ─────────────────────────────────────────────

    /** Slack band: floor &le; 0.55 — comfortable margin. */
    static final String BAND_HIGH_SLACK = "HIGH_SLACK";

    /** Slack band: 0.56 – 0.70 — some urgency pressure building. */
    static final String BAND_MODERATE_SLACK = "MODERATE_SLACK";

    /** Slack band: 0.71 – 0.85 — urgency pressure is significant. */
    static final String BAND_LOW_SLACK = "LOW_SLACK";

    /** Slack band: 0.86 – 0.95 — nearly all capacity consumed by urgent outcomes. */
    static final String BAND_NO_SLACK = "NO_SLACK";

    private final OutcomeMetadataRepository outcomeMetadataRepository;

    /**
     * Reserved for future use: outcome name resolution for enriched responses
     * in Phase 4 per-team scoping.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final RcdoClient rcdoClient;

    /**
     * Production constructor — Spring auto-wires all dependencies.
     *
     * @param outcomeMetadataRepository repository for outcome metadata
     * @param rcdoClient                RCDO client for resolving outcome names (Phase 4)
     */
    @Autowired
    public StrategicSlackService(
            OutcomeMetadataRepository outcomeMetadataRepository,
            RcdoClient rcdoClient
    ) {
        this.outcomeMetadataRepository = outcomeMetadataRepository;
        this.rcdoClient = rcdoClient;
    }

    /**
     * Computes the strategic slack for the given organisation.
     *
     * <p>The {@code managerId} parameter is accepted for API compatibility but is not
     * used in Phase 3 — urgency is computed org-wide per spec §7. Phase 4 will use
     * this parameter to scope the calculation to the manager's direct-report teams.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager's user ID (reserved for Phase 4 per-team scoping)
     * @return a {@link SlackInfo} record with the computed floor, band, and risk counts
     */
    @Transactional(readOnly = true)
    public SlackInfo computeStrategicSlack(UUID orgId, UUID managerId) {
        List<OutcomeMetadataEntity> outcomes = outcomeMetadataRepository.findByOrgId(orgId);

        double floor = FLOOR_BASE;
        int atRiskCount = 0;
        int criticalCount = 0;

        for (OutcomeMetadataEntity outcome : outcomes) {
            String band = outcome.getUrgencyBand();
            if (band == null) {
                continue;
            }
            switch (band) {
                case UrgencyComputeService.BAND_CRITICAL -> {
                    floor += CRITICAL_INCREMENT;
                    criticalCount++;
                }
                case UrgencyComputeService.BAND_AT_RISK -> {
                    floor += AT_RISK_INCREMENT;
                    atRiskCount++;
                }
                case UrgencyComputeService.BAND_NEEDS_ATTENTION -> floor += NEEDS_ATTENTION_INCREMENT;
                default -> {
                    // ON_TRACK and NO_TARGET do not contribute to urgency pressure
                }
            }
        }

        floor = Math.min(floor, FLOOR_CAP);

        String slackBand = computeSlackBand(floor);
        BigDecimal strategicFocusFloor = BigDecimal.valueOf(floor).setScale(2, RoundingMode.HALF_UP);

        return new SlackInfo(slackBand, strategicFocusFloor, atRiskCount, criticalCount);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Maps the computed {@code floor} value to a slack band string.
     *
     * @param floor the strategic focus floor in [{@value #FLOOR_BASE}, {@value #FLOOR_CAP}]
     * @return one of {@link #BAND_HIGH_SLACK}, {@link #BAND_MODERATE_SLACK},
     *         {@link #BAND_LOW_SLACK}, or {@link #BAND_NO_SLACK}
     */
    private String computeSlackBand(double floor) {
        if (floor <= HIGH_SLACK_THRESHOLD) {
            return BAND_HIGH_SLACK;
        } else if (floor <= MODERATE_SLACK_THRESHOLD) {
            return BAND_MODERATE_SLACK;
        } else if (floor <= LOW_SLACK_THRESHOLD) {
            return BAND_LOW_SLACK;
        } else {
            return BAND_NO_SLACK;
        }
    }
}
