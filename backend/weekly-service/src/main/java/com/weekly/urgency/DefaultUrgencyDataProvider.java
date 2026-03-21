package com.weekly.urgency;

import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link UrgencyDataProvider}, bridging the urgency
 * package's internal persistence layer to the shared interface consumed by
 * downstream phases (Phase 2/4/5).
 *
 * <p>Resolves outcome names via {@link RcdoClient}, reads urgency metadata from
 * {@link OutcomeMetadataRepository}, and delegates strategic slack to
 * {@link StrategicSlackService}.
 *
 * <p>Uses an injectable {@link Clock} for testability, following the
 * {@link UrgencyComputeService} pattern.
 */
@Service
public class DefaultUrgencyDataProvider implements UrgencyDataProvider {

    private final OutcomeMetadataRepository outcomeMetadataRepository;
    private final StrategicSlackService strategicSlackService;
    private final RcdoClient rcdoClient;
    private final Clock clock;

    /**
     * Production constructor — Spring auto-wires all dependencies and uses
     * {@link Clock#systemUTC()} for date arithmetic.
     *
     * @param outcomeMetadataRepository repository for outcome metadata
     * @param strategicSlackService     service for strategic slack computation
     * @param rcdoClient                RCDO client for resolving outcome names
     */
    @Autowired
    public DefaultUrgencyDataProvider(
            OutcomeMetadataRepository outcomeMetadataRepository,
            StrategicSlackService strategicSlackService,
            RcdoClient rcdoClient
    ) {
        this(outcomeMetadataRepository, strategicSlackService, rcdoClient, Clock.systemUTC());
    }

    /**
     * Package-private constructor for unit tests — allows injecting a fixed
     * {@link Clock} so date-sensitive assertions are deterministic.
     */
    DefaultUrgencyDataProvider(
            OutcomeMetadataRepository outcomeMetadataRepository,
            StrategicSlackService strategicSlackService,
            RcdoClient rcdoClient,
            Clock clock
    ) {
        this.outcomeMetadataRepository = outcomeMetadataRepository;
        this.strategicSlackService = strategicSlackService;
        this.rcdoClient = rcdoClient;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the metadata row for {@code (orgId, outcomeId)}, resolves the
     * outcome display name from {@link RcdoClient}, computes {@code daysRemaining}
     * and {@code expectedProgressPct} against today's date, and returns a
     * {@link UrgencyInfo} record.
     *
     * @return {@link UrgencyInfo} for the outcome, or {@code null} if no metadata
     *         row exists for the given {@code (orgId, outcomeId)} pair
     */
    @Override
    @Transactional(readOnly = true)
    public UrgencyInfo getOutcomeUrgency(UUID orgId, UUID outcomeId) {
        Optional<OutcomeMetadataEntity> metadataOpt =
                outcomeMetadataRepository.findByOrgIdAndOutcomeId(orgId, outcomeId);
        if (metadataOpt.isEmpty()) {
            return null;
        }
        return toUrgencyInfo(orgId, metadataOpt.get());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches all metadata rows for the given org and maps each to a
     * {@link UrgencyInfo} record via {@link #toUrgencyInfo}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UrgencyInfo> getOrgUrgencySummary(UUID orgId) {
        List<OutcomeMetadataEntity> all = outcomeMetadataRepository.findByOrgId(orgId);
        return all.stream()
                .map(metadata -> toUrgencyInfo(orgId, metadata))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates directly to {@link StrategicSlackService#computeStrategicSlack}.
     */
    @Override
    public SlackInfo getStrategicSlack(UUID orgId, UUID managerId) {
        return strategicSlackService.computeStrategicSlack(orgId, managerId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Converts a {@link OutcomeMetadataEntity} to a {@link UrgencyInfo} record.
     *
     * <p>Steps:
     * <ol>
     *   <li>Resolve outcome name from {@link RcdoClient#getOutcome} (null if not found).</li>
     *   <li>Compute {@code daysRemaining} = days from today to targetDate
     *       ({@link Long#MIN_VALUE} if no targetDate).</li>
     *   <li>Compute {@code expectedProgressPct} via linear interpolation from
     *       {@code createdAt} to {@code targetDate} ({@code null} if no targetDate).</li>
     *   <li>Use the stored {@code urgencyBand}; defaults to
     *       {@link UrgencyComputeService#BAND_NO_TARGET} if the band has not yet
     *       been computed.</li>
     * </ol>
     *
     * @param orgId    the organisation ID (for RcdoClient lookup)
     * @param metadata the outcome metadata entity
     * @return a fully populated {@link UrgencyInfo} record
     */
    private UrgencyInfo toUrgencyInfo(UUID orgId, OutcomeMetadataEntity metadata) {
        UUID outcomeId = metadata.getOutcomeId();

        String outcomeName = resolveOutcomeName(orgId, outcomeId);

        LocalDate targetDate = metadata.getTargetDate();
        LocalDate today = LocalDate.now(clock);

        long daysRemaining;
        BigDecimal expectedProgressPct;

        if (targetDate == null) {
            daysRemaining = Long.MIN_VALUE;
            expectedProgressPct = null;
        } else {
            daysRemaining = ChronoUnit.DAYS.between(today, targetDate);
            expectedProgressPct = computeExpectedProgressPct(metadata, today, targetDate);
        }

        String urgencyBand = metadata.getUrgencyBand();
        if (urgencyBand == null) {
            urgencyBand = UrgencyComputeService.BAND_NO_TARGET;
        }

        return new UrgencyInfo(
                outcomeId,
                outcomeName,
                targetDate,
                metadata.getProgressPct(),
                expectedProgressPct,
                urgencyBand,
                daysRemaining
        );
    }

    /**
     * Looks up the outcome display name via {@link RcdoClient#getOutcome}.
     *
     * @param orgId     the organisation ID
     * @param outcomeId the outcome ID
     * @return the outcome name, or {@code null} if the outcome is not found in the RCDO tree
     */
    private String resolveOutcomeName(UUID orgId, UUID outcomeId) {
        Optional<RcdoOutcomeDetail> detail = rcdoClient.getOutcome(orgId, outcomeId);
        return detail.map(RcdoOutcomeDetail::outcomeName).orElse(null);
    }

    /**
     * Computes the expected progress percentage at {@code today} using linear
     * interpolation over the tracking period [{@code createdAt}, {@code targetDate}].
     *
     * <p>Expected progress is clamped to [0, 100]. If {@code targetDate} is on or
     * before {@code createdAt} the outcome is considered 100 % expected (past due).
     *
     * @param metadata   the outcome metadata entity (provides {@code createdAt})
     * @param today      the reference date for the computation
     * @param targetDate the target completion date (non-null)
     * @return expected progress as a percentage in [0, 100], scaled to 2 decimal places
     */
    private BigDecimal computeExpectedProgressPct(
            OutcomeMetadataEntity metadata,
            LocalDate today,
            LocalDate targetDate
    ) {
        LocalDate startDate = metadata.getCreatedAt()
                .atZone(clock.getZone())
                .toLocalDate();

        long daysTotal = ChronoUnit.DAYS.between(startDate, targetDate);
        if (daysTotal <= 0) {
            return BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP);
        }

        long daysElapsed = ChronoUnit.DAYS.between(startDate, today);
        double expectedRatio = Math.max(0.0, Math.min(1.0, (double) daysElapsed / (double) daysTotal));
        return BigDecimal.valueOf(expectedRatio * 100.0).setScale(2, RoundingMode.HALF_UP);
    }
}
