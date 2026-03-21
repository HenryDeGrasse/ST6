package com.weekly.shared;

/**
 * Severity level for an overcommitment warning.
 *
 * <ul>
 *   <li>{@code NONE}     – adjusted total is within the realistic weekly cap.</li>
 *   <li>{@code MODERATE} – adjusted total exceeds the cap but not by more than 20%.</li>
 *   <li>{@code HIGH}     – adjusted total exceeds the cap by more than 20%.</li>
 * </ul>
 */
public enum OvercommitLevel {
    NONE,
    MODERATE,
    HIGH
}
