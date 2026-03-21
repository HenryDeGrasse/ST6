package com.weekly.quickupdate;

/**
 * A single AI- or pattern-generated progress update option for the Quick Update card.
 *
 * @param text   the short phrase (under 50 characters) presented to the user
 * @param source the origin of the suggestion, e.g. {@code "ai"} or {@code "pattern"}
 */
public record CheckInOptionItem(String text, String source) {}
