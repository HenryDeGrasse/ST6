package com.weekly.capacity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Detects whether a user's committed work for a week exceeds their realistic capacity.
 *
 * <p>Each commit's estimated hours is adjusted by a per-category bias factor derived
 * from the user's historical {@link CapacityProfileEntity}.  The bias factor represents
 * the ratio of actual-to-estimated hours observed for that category; multiplying an
 * estimate by its bias produces a "realism-adjusted" expected hours figure.
 *
 * <p>The adjusted weekly total is then compared against the user's
 * {@code realisticWeeklyCap}:
 * <ul>
 *   <li>&gt; cap × 1.2  → {@link OvercommitLevel#HIGH}</li>
 *   <li>&gt; cap        → {@link OvercommitLevel#MODERATE}</li>
 *   <li>otherwise       → {@link OvercommitLevel#NONE}</li>
 * </ul>
 *
 * <p>Returns {@link OvercommitLevel#NONE} (in an {@link OvercommitWarning}) if the
 * profile is {@code null} or lacks the data needed for a meaningful comparison.
 */
@Service
public class OvercommitDetector {

    private static final Logger LOG = LoggerFactory.getLogger(OvercommitDetector.class);
    private static final BigDecimal HIGH_THRESHOLD_MULTIPLIER = new BigDecimal("1.2");

    private final ObjectMapper objectMapper;

    public OvercommitDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates whether the given commits exceed the user's capacity profile.
     *
     * @param commits list of commits for the current week (may be empty)
     * @param profile the user's capacity profile (may be {@code null})
     * @return an {@link OvercommitWarning}; level is {@link OvercommitLevel#NONE}
     *         when a determination cannot be made
     */
    public OvercommitWarning detectOvercommitment(
            List<WeeklyCommitEntity> commits, CapacityProfileEntity profile) {

        // Cannot detect without a profile or meaningful cap data
        if (profile == null
                || profile.getRealisticWeeklyCap() == null
                || profile.getRealisticWeeklyCap().compareTo(BigDecimal.ZERO) <= 0
                || profile.getWeeksAnalyzed() <= 0) {
            return noneWarning(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal cap = profile.getRealisticWeeklyCap();
        Map<String, BigDecimal> categoryBiasMap = parseCategoryBias(profile.getCategoryBiasJson());
        BigDecimal globalBias = profile.getEstimationBias();

        // Sum bias-adjusted estimated hours
        BigDecimal adjustedTotal = BigDecimal.ZERO;
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getEstimatedHours() == null
                    || commit.getEstimatedHours().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal bias = resolveBias(commit, categoryBiasMap, globalBias);
            if (bias != null) {
                adjustedTotal = adjustedTotal.add(commit.getEstimatedHours().multiply(bias));
            } else {
                // No bias data available; treat estimate as-is
                adjustedTotal = adjustedTotal.add(commit.getEstimatedHours());
            }
        }

        adjustedTotal = adjustedTotal.setScale(1, RoundingMode.HALF_UP);

        // Compare adjusted total against cap
        BigDecimal highThreshold = cap.multiply(HIGH_THRESHOLD_MULTIPLIER);
        if (adjustedTotal.compareTo(highThreshold) > 0) {
            String msg = String.format(
                    "Adjusted committed hours (%.1f h) exceed your realistic weekly cap (%.1f h) "
                            + "by more than 20%%. Consider reducing your commitments.",
                    adjustedTotal, cap);
            return new OvercommitWarning(OvercommitLevel.HIGH, msg, adjustedTotal, cap);
        } else if (adjustedTotal.compareTo(cap) > 0) {
            String msg = String.format(
                    "Adjusted committed hours (%.1f h) exceed your realistic weekly cap (%.1f h). "
                            + "You may be overcommitted.",
                    adjustedTotal, cap);
            return new OvercommitWarning(OvercommitLevel.MODERATE, msg, adjustedTotal, cap);
        } else {
            return noneWarning(adjustedTotal, cap);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the bias factor to apply to a commit's estimated hours.
     *
     * <p>Tries the per-category bias first; falls back to the global estimation bias.
     * Returns {@code null} when no bias data is available at all.
     */
    private BigDecimal resolveBias(
            WeeklyCommitEntity commit,
            Map<String, BigDecimal> categoryBiasMap,
            BigDecimal globalBias) {

        if (commit.getCategory() != null) {
            BigDecimal categoryBias = categoryBiasMap.get(commit.getCategory().name());
            if (categoryBias != null) {
                return categoryBias;
            }
        }
        return globalBias;
    }

    /**
     * Parses the {@code categoryBiasJson} stored on a profile into a map of
     * category name → bias factor.
     *
     * <p>The JSON is expected to be an array of objects of the form:
     * <pre>{@code [{"category":"DELIVERY","bias":0.82}, ...]}</pre>
     * Missing or unparseable JSON produces an empty map (graceful degradation).
     */
    private Map<String, BigDecimal> parseCategoryBias(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> entries =
                    objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, BigDecimal> result = new HashMap<>();
            for (Map<String, Object> entry : entries) {
                Object cat = entry.get("category");
                Object biasVal = entry.get("bias");
                if (cat == null || biasVal == null) {
                    continue;
                }
                try {
                    BigDecimal bias = new BigDecimal(biasVal.toString());
                    if (bias.compareTo(BigDecimal.ZERO) > 0) {
                        result.put(cat.toString(), bias);
                    }
                } catch (NumberFormatException nfe) {
                    LOG.debug("Skipping non-numeric bias value '{}' for category '{}'",
                            biasVal, cat);
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse categoryBiasJson; falling back to global bias", e);
            return Map.of();
        }
    }

    private static OvercommitWarning noneWarning(BigDecimal adjustedTotal, BigDecimal cap) {
        return new OvercommitWarning(OvercommitLevel.NONE, "", adjustedTotal, cap);
    }
}
