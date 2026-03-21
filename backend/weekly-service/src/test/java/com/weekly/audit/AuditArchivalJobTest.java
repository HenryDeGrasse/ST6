package com.weekly.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link AuditArchivalJob}.
 */
class AuditArchivalJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-18T04:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private AuditEventRepository auditEventRepository;
    private AuditArchivalJob archivalJob;

    @BeforeEach
    void setUp() {
        auditEventRepository = mock(AuditEventRepository.class);
        archivalJob = new AuditArchivalJob(auditEventRepository, 5, FIXED_CLOCK);
    }

    // ── Constructor validation ───────────────────────────────

    @Test
    void rejectsNonPositiveArchiveAfterYears() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AuditArchivalJob(auditEventRepository, 0, FIXED_CLOCK));

        assertEquals("weekly.audit.archival.archive-after-years must be greater than 0",
                ex.getMessage());
    }

    // ── Accessor tests ───────────────────────────────────────

    @Test
    void returnsConfiguredArchiveAfterYears() {
        assertEquals(5, archivalJob.getArchiveAfterYears());
    }

    // ── identifyArchivableEvents — no orgs ──────────────────

    @Test
    void doesNothingWhenNoOrgsExist() {
        when(auditEventRepository.findDistinctOrgIds()).thenReturn(Collections.emptyList());

        archivalJob.identifyArchivableEvents();

        verify(auditEventRepository).findDistinctOrgIds();
    }

    // ── identifyArchivableEvents — archival cutoff query ───

    @Test
    void countsOnlyEventsReturnedByAgeFilteredQuery() {
        UUID orgId = UUID.randomUUID();
        Instant archivalCutoff = FIXED_NOW.minus(5 * 365L, ChronoUnit.DAYS);

        Page<AuditEventEntity> firstPage = new PageImpl<>(
                List.of(stubEvent(orgId), stubEvent(orgId)),
                PageRequest.of(0, AuditArchivalJob.PAGE_SIZE),
                2);

        when(auditEventRepository.findDistinctOrgIds()).thenReturn(List.of(orgId));
        when(auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), any(Pageable.class)))
                .thenReturn(firstPage);

        archivalJob.identifyArchivableEvents();

        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), any(Pageable.class));
    }

    @Test
    void treatsEmptyArchivalQueryResultAsZeroEligibleEvents() {
        UUID orgId = UUID.randomUUID();
        Instant archivalCutoff = FIXED_NOW.minus(5 * 365L, ChronoUnit.DAYS);
        Page<AuditEventEntity> emptyPage = Page.empty(PageRequest.of(0, AuditArchivalJob.PAGE_SIZE));

        when(auditEventRepository.findDistinctOrgIds()).thenReturn(List.of(orgId));
        when(auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), any(Pageable.class)))
                .thenReturn(emptyPage);

        archivalJob.identifyArchivableEvents();

        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), any(Pageable.class));
    }

    @Test
    void countsEligibleEventsAcrossMultiplePagesForSameOrg() {
        UUID orgId = UUID.randomUUID();
        Instant archivalCutoff = FIXED_NOW.minus(5 * 365L, ChronoUnit.DAYS);
        Page<AuditEventEntity> firstPage = new PageImpl<>(
                List.of(stubEvent(orgId)),
                PageRequest.of(0, AuditArchivalJob.PAGE_SIZE),
                AuditArchivalJob.PAGE_SIZE + 1L);
        Page<AuditEventEntity> secondPage = new PageImpl<>(
                List.of(stubEvent(orgId)),
                PageRequest.of(1, AuditArchivalJob.PAGE_SIZE),
                AuditArchivalJob.PAGE_SIZE + 1L);

        when(auditEventRepository.findDistinctOrgIds()).thenReturn(List.of(orgId));
        when(auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), any(Pageable.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        archivalJob.identifyArchivableEvents();

        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), eq(PageRequest.of(0, AuditArchivalJob.PAGE_SIZE)));
        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(orgId), eq(archivalCutoff), eq(PageRequest.of(1, AuditArchivalJob.PAGE_SIZE)));
    }

    @Test
    void scansAllOrganisationsIndependently() {
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        Instant archivalCutoff = FIXED_NOW.minus(5 * 365L, ChronoUnit.DAYS);

        Page<AuditEventEntity> page1 = new PageImpl<>(List.of(stubEvent(org1)),
                PageRequest.of(0, AuditArchivalJob.PAGE_SIZE), 1);
        Page<AuditEventEntity> page2 = new PageImpl<>(List.of(stubEvent(org2)),
                PageRequest.of(0, AuditArchivalJob.PAGE_SIZE), 1);

        when(auditEventRepository.findDistinctOrgIds()).thenReturn(List.of(org1, org2));
        when(auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(org1), eq(archivalCutoff), any(Pageable.class)))
                .thenReturn(page1);
        when(auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(org2), eq(archivalCutoff), any(Pageable.class)))
                .thenReturn(page2);

        archivalJob.identifyArchivableEvents();

        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(org1), eq(archivalCutoff), any(Pageable.class));
        verify(auditEventRepository).findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(org2), eq(archivalCutoff), any(Pageable.class));
    }

    @Test
    void respectsCustomArchiveAfterYears() {
        AuditArchivalJob customJob = new AuditArchivalJob(auditEventRepository, 7, FIXED_CLOCK);
        assertEquals(7, customJob.getArchiveAfterYears());
    }

    private AuditEventEntity stubEvent(UUID orgId) {
        AuditEventEntity event = new AuditEventEntity(
                orgId,
                UUID.randomUUID(),
                "TEST_ACTION",
                "PLAN",
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null
        );
        event.setHash("dummyhash");
        return event;
    }
}
