package com.weekly.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding a comment to an issue (Phase 6).
 */
public record AddCommentRequest(@NotBlank @Size(max = 10000) String commentText) {}
