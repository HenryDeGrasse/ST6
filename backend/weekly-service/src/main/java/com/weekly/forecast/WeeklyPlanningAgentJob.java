package com.weekly.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger for the proactive weekly-planning agent. */
@Component
@ConditionalOnProperty(name = "ai.features.weekly-planning-agent-enabled", havingValue = "true")
public class WeeklyPlanningAgentJob {

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyPlanningAgentJob.class);

    private final WeeklyPlanningAgentService weeklyPlanningAgentService;

    public WeeklyPlanningAgentJob(WeeklyPlanningAgentService weeklyPlanningAgentService) {
        this.weeklyPlanningAgentService = weeklyPlanningAgentService;
    }

    @Scheduled(cron = "${weekly-planning-agent.cron:0 0 6 * * MON}", zone = "UTC")
    public void createDrafts() {
        int drafted = weeklyPlanningAgentService.createDraftsForCurrentWeek();
        LOG.info("WeeklyPlanningAgentJob: created {} proactive draft notification(s)", drafted);
    }
}
