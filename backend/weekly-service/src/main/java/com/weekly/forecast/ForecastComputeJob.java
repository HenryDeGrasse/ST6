package com.weekly.forecast;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that recomputes the persisted latest target-date forecast per outcome.
 */
@Component
@ConditionalOnProperty(name = "ai.features.target-date-forecasting-enabled", havingValue = "true")
public class ForecastComputeJob {

    static final String COUNTER_FORECAST_RECOMPUTE_TOTAL = "forecast_recompute_total";

    private static final Logger LOG = LoggerFactory.getLogger(ForecastComputeJob.class);

    private final TargetDateForecastService targetDateForecastService;
    private final MeterRegistry meterRegistry;

    public ForecastComputeJob(
            TargetDateForecastService targetDateForecastService,
            MeterRegistry meterRegistry) {
        this.targetDateForecastService = targetDateForecastService;
        this.meterRegistry = meterRegistry;
    }

    /** Recomputes persisted forecasts across all orgs with recent planning activity. */
    @Scheduled(cron = "${forecast.compute.cron:0 15 * * * *}")
    public void recomputeAll() {
        List<UUID> orgIds = targetDateForecastService.getForecastableOrgIds();
        if (orgIds.isEmpty()) {
            LOG.debug("ForecastComputeJob: no orgs with planning activity found, skipping");
            return;
        }

        LOG.info("ForecastComputeJob: starting recomputation for {} org(s)", orgIds.size());
        int successCount = 0;
        for (UUID orgId : orgIds) {
            try {
                int recomputed = targetDateForecastService.recomputeForecastsForOrg(orgId).size();
                meterRegistry.counter(COUNTER_FORECAST_RECOMPUTE_TOTAL).increment();
                successCount++;
                LOG.debug("ForecastComputeJob: recomputed {} forecast(s) for org {}", recomputed, orgId);
            } catch (Exception e) {
                LOG.warn("ForecastComputeJob: error recomputing forecasts for org {}: {}",
                        orgId, e.getMessage(), e);
            }
        }

        LOG.info("ForecastComputeJob: recomputation complete — {}/{} org(s) succeeded",
                successCount, orgIds.size());
    }
}
