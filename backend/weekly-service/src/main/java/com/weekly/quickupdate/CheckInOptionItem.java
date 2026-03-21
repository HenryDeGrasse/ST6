package com.weekly.quickupdate;

/**
 * A single learned or AI-generated progress update option for the Quick Update card.
 *
 * @param text   the short phrase (under 50 characters) presented to the user
 * @param source deterministic suggestion origin: {@code "user_history"},
 *               {@code "team_common"}, or {@code "ai_generated"}
 */
public record CheckInOptionItem(String text, String source) {}
