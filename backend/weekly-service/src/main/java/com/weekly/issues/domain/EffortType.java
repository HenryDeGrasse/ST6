package com.weekly.issues.domain;

/**
 * Effort-type classification for backlog issues (Phase 6).
 *
 * <p>Replaces the 7-value {@code CommitCategory} with a simpler 4-value
 * taxonomy. The AI suggests effort type on issue creation based on title,
 * description, and RCDO context.
 */
public enum EffortType {
    /** Creating something new — features, tools, content, infrastructure. */
    BUILD,
    /** Keeping things running — ops, bugs, incidents, tech debt. */
    MAINTAIN,
    /** Working with/for others — reviews, mentoring, meetings, customer work. */
    COLLABORATE,
    /** Investing in growth — spikes, training, research, experiments. */
    LEARN
}
