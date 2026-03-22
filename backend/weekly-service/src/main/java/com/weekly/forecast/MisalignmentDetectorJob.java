package com.weekly.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger for the manager-facing misalignment detector. */
@Component
@ConditionalOnProperty(name = "ai.features.misalignment-agent-enabled", havingValue = "true")
public class MisalignmentDetectorJob {

    private static final Logger LOG = LoggerFactory.getLogger(MisalignmentDetectorJob.class);

    private final MisalignmentDetectorService misalignmentDetectorService;

    public MisalignmentDetectorJob(MisalignmentDetectorService misalignmentDetectorService) {
        this.misalignmentDetectorService = misalignmentDetectorService;
    }

    @Scheduled(cron = "${misalignment-detector.cron:0 30 18 * * *}", zone = "UTC")
    public void detect() {
        int notifications = misalignmentDetectorService.detectCurrentWeekMisalignment();
        LOG.info("MisalignmentDetectorJob: created {} manager briefing notification(s)", notifications);
    }
}
