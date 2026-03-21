package com.weekly.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NotificationRetentionJob}.
 */
class NotificationRetentionJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-14T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private NotificationRepository notificationRepository;
    private NotificationRetentionJob retentionJob;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        retentionJob = new NotificationRetentionJob(notificationRepository, 90, FIXED_CLOCK);
    }

    @Test
    void purgesNotificationsOlderThanRetentionPeriod() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(90L * 24 * 60 * 60);
        when(notificationRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(5);

        retentionJob.purgeExpiredNotifications();

        verify(notificationRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void handlesNoExpiredNotificationsGracefully() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(90L * 24 * 60 * 60);
        when(notificationRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(0);

        retentionJob.purgeExpiredNotifications();

        verify(notificationRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void usesConfiguredRetentionDays() {
        NotificationRetentionJob customJob = new NotificationRetentionJob(notificationRepository, 30, FIXED_CLOCK);
        Instant expectedCutoff = FIXED_NOW.minusSeconds(30L * 24 * 60 * 60);
        assertEquals(30, customJob.getRetentionDays());
        when(notificationRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(3);

        customJob.purgeExpiredNotifications();

        verify(notificationRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void defaultRetentionIs90Days() {
        assertEquals(90, retentionJob.getRetentionDays());
    }

    @Test
    void rejectsNonPositiveRetentionDays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NotificationRetentionJob(notificationRepository, 0, FIXED_CLOCK));

        assertEquals("weekly.notification.retention-days must be greater than 0", exception.getMessage());
    }
}
