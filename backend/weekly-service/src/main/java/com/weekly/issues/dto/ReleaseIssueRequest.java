package com.weekly.issues.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for releasing an issue from its current weekly assignment (Phase 6).
 */
public record ReleaseIssueRequest(@NotNull UUID weeklyPlanId) {}
