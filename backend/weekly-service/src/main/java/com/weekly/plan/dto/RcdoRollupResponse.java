package com.weekly.plan.dto;

import java.util.List;

/**
 * RCDO roll-up response for the manager dashboard.
 */
public record RcdoRollupResponse(
        String weekStart,
        List<RcdoRollupItem> items,
        int nonStrategicCount
) {}
