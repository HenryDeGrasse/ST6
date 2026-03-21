package com.weekly.capacity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the estimation-coaching endpoint.
 *
 * <h3>Endpoint</h3>
 * <ul>
 *   <li>{@code GET /api/v1/users/me/estimation-coaching?planId=X} — returns coaching
 *       insights combining this week's estimated vs actual hours with the user's
 *       historical capacity profile patterns.</li>
 * </ul>
 *
 * <p>The caller's identity ({@code orgId}, {@code userId}) is sourced exclusively
 * from the validated {@link com.weekly.auth.UserPrincipal} exposed through the
 * request-scoped {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class EstimationCoachingController {

    private static final Logger LOG = LoggerFactory.getLogger(EstimationCoachingController.class);

    /**
     * Minimum absolute deviation from 1.0 for a category bias to be considered
     * "significant" and therefore warrant a coaching tip.
     */
    private static final double SIGNIFICANT_BIAS_THRESHOLD = 0.15;

    private final AuthenticatedUserContext authenticatedUserContext;
    private final CapacityProfileService capacityProfileService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final WeeklyCommitActualRepository weeklyCommitActualRepository;
    private final ObjectMapper objectMapper;

    public EstimationCoachingController(
            AuthenticatedUserContext authenticatedUserContext,
            CapacityProfileService capacityProfileService,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            WeeklyCommitActualRepository weeklyCommitActualRepository,
            ObjectMapper objectMapper) {
        this.authenticatedUserContext = authenticatedUserContext;
        this.capacityProfileService = capacityProfileService;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.weeklyCommitActualRepository = weeklyCommitActualRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/v1/users/me/estimation-coaching?planId=X
     *
     * <p>Returns estimation coaching insights for the authenticated user:
     * <ul>
     *   <li>This week's total estimated and actual hours for the given plan</li>
     *   <li>Accuracy ratio (actual / estimated) for this week</li>
     *   <li>Historical estimation bias and confidence level from the rolling profile</li>
     *   <li>Per-category bias breakdown with coaching tips for significant bias</li>
     *   <li>Per-priority completion-rate statistics from historical data</li>
     * </ul>
     *
     * @param planId the plan whose commits and actuals to summarise
     * @return 200 with {@link EstimationCoachingResponse}, or 404 if the plan does not
     *         belong to the authenticated user
     */
    @GetMapping("/estimation-coaching")
    public ResponseEntity<?> getEstimationCoaching(@RequestParam UUID planId) {
        UUID orgId = authenticatedUserContext.orgId();
        UUID userId = authenticatedUserContext.userId();

        // 1. Validate planId belongs to the authenticated user
        Optional<WeeklyPlanEntity> planOpt = weeklyPlanRepository.findByOrgIdAndId(orgId, planId);
        if (planOpt.isEmpty() || !planOpt.get().getOwnerUserId().equals(userId)) {
            return ResponseEntity
                    .status(ErrorCode.NOT_FOUND.getHttpStatus())
                    .body(ApiErrorResponse.of(
                            ErrorCode.NOT_FOUND,
                            "Plan not found: " + planId));
        }

        // 2. Load commits + actuals for the plan
        List<WeeklyCommitEntity> commits =
                weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = commitIds.isEmpty()
                ? Map.of()
                : weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId, Function.identity()));

        // 3. Compute this week's totals
        BigDecimal totalEstimated = commits.stream()
                .filter(c -> c.getEstimatedHours() != null)
                .map(WeeklyCommitEntity::getEstimatedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActual = actualsByCommitId.values().stream()
                .filter(a -> a.getActualHours() != null)
                .map(WeeklyCommitActualEntity::getActualHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double thisWeekEstimated = totalEstimated.doubleValue();
        double thisWeekActual = totalActual.doubleValue();
        Double accuracyRatio = totalEstimated.compareTo(BigDecimal.ZERO) > 0
                ? totalActual.divide(totalEstimated, 2, RoundingMode.HALF_UP).doubleValue()
                : null;

        // 4. Load capacity profile for rolling patterns
        Optional<CapacityProfileEntity> profileOpt =
                capacityProfileService.getProfile(orgId, userId);
        Double overallBias = profileOpt
                .map(p -> p.getEstimationBias() != null
                        ? p.getEstimationBias().doubleValue()
                        : null)
                .orElse(null);
        String confidenceLevel = profileOpt
                .map(CapacityProfileEntity::getConfidenceLevel)
                .orElse("LOW");

        // 5. Build category and priority insights from the rolling profile
        List<CategoryInsight> categoryInsights = profileOpt
                .map(p -> buildCategoryInsights(p.getCategoryBiasJson()))
                .orElse(List.of());
        List<PriorityInsight> priorityInsights = profileOpt
                .map(p -> buildPriorityInsights(p.getPriorityCompletionJson()))
                .orElse(List.of());

        EstimationCoachingResponse response = new EstimationCoachingResponse(
                thisWeekEstimated,
                thisWeekActual,
                accuracyRatio,
                overallBias,
                confidenceLevel,
                categoryInsights,
                priorityInsights);

        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses the {@code categoryBiasJson} from a capacity profile and builds
     * {@link CategoryInsight} objects, including a coaching tip for categories
     * whose bias deviates from 1.0 by at least {@link #SIGNIFICANT_BIAS_THRESHOLD}.
     *
     * <p>When {@code bias > 1}: actuals consistently exceeded estimates → suggest a buffer.
     * When {@code bias < 1}: estimates consistently exceeded actuals → suggest reducing.
     */
    private List<CategoryInsight> buildCategoryInsights(String categoryBiasJson) {
        if (categoryBiasJson == null || categoryBiasJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> entries =
                    objectMapper.readValue(categoryBiasJson, new TypeReference<>() {});
            List<CategoryInsight> result = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                Object catVal = entry.get("category");
                Object biasVal = entry.get("bias");
                if (catVal == null) {
                    continue;
                }
                String category = catVal.toString();

                Double bias = null;
                if (biasVal != null) {
                    try {
                        bias = Double.parseDouble(biasVal.toString());
                    } catch (NumberFormatException e) {
                        LOG.debug("Skipping non-numeric bias '{}' for category '{}'", biasVal, category);
                        continue;
                    }
                }

                String tip = null;
                if (bias != null) {
                    double deviation = Math.abs(1.0 - bias);
                    if (deviation >= SIGNIFICANT_BIAS_THRESHOLD) {
                        if (bias > 1.0) {
                            // Actuals exceeded estimates — underestimating
                            int bufferPct = (int) Math.round((bias - 1.0) * 100);
                            tip = String.format(
                                    "Consider adding %d%% buffer to %s estimates.",
                                    bufferPct, category);
                        } else {
                            // Estimates exceeded actuals — overestimating
                            int reducePct = (int) Math.round((1.0 - bias) * 100);
                            tip = String.format(
                                    "You tend to overestimate %s tasks; "
                                            + "consider reducing estimates by ~%d%%.",
                                    category, reducePct);
                        }
                    }
                }
                result.add(new CategoryInsight(category, bias, tip));
            }
            return result;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse categoryBiasJson for coaching insights", e);
            return List.of();
        }
    }

    /**
     * Parses the {@code priorityCompletionJson} from a capacity profile and builds
     * {@link PriorityInsight} objects.
     */
    private List<PriorityInsight> buildPriorityInsights(String priorityCompletionJson) {
        if (priorityCompletionJson == null || priorityCompletionJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> entries =
                    objectMapper.readValue(priorityCompletionJson, new TypeReference<>() {});
            List<PriorityInsight> result = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                Object priorityVal = entry.get("priority");
                Object doneRateVal = entry.get("doneRate");
                Object sampleSizeVal = entry.get("sampleSize");
                if (priorityVal == null || doneRateVal == null || sampleSizeVal == null) {
                    continue;
                }
                String priority = priorityVal.toString();
                double completionRate;
                int sampleSize;
                try {
                    completionRate = Double.parseDouble(doneRateVal.toString());
                    sampleSize = Integer.parseInt(sampleSizeVal.toString());
                } catch (NumberFormatException e) {
                    LOG.debug("Skipping malformed priority entry for '{}'", priority);
                    continue;
                }
                result.add(new PriorityInsight(priority, completionRate, sampleSize));
            }
            return result;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse priorityCompletionJson for coaching insights", e);
            return List.of();
        }
    }
}
