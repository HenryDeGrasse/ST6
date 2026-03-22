package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachingOrgGraphClientDecorator}.
 *
 * <p>Covers: write-through on success, fallback on delegate failure,
 * rethrow when cache is cold, and TTL expiry.
 */
class CachingOrgGraphClientDecoratorTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_1 = UUID.fromString("c0000000-0000-0000-0000-000000000010");
    private static final UUID REPORT_2 = UUID.fromString("c0000000-0000-0000-0000-000000000020");
    private static final List<UUID> REPORTS = List.of(REPORT_1, REPORT_2);
    private static final List<DirectReport> REPORTS_WITH_NAMES = List.of(
            new DirectReport(REPORT_1, "Ada"),
            new DirectReport(REPORT_2, "Grace")
    );
    private static final List<OrgRosterEntry> ROSTER = List.of(
            new OrgRosterEntry(MANAGER_ID, "Grace Hopper", null, "America/New_York"),
            new OrgRosterEntry(REPORT_1, "Ada Lovelace", MANAGER_ID, "Europe/London"),
            new OrgRosterEntry(REPORT_2, "Katherine Johnson", MANAGER_ID, "America/Chicago")
    );

    private OrgGraphClient delegate;
    private CachingOrgGraphClientDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(OrgGraphClient.class);
        decorator = new CachingOrgGraphClientDecorator(delegate);
    }

    @Test
    void getDelegateReturnsWrappedClient() {
        assertSame(delegate, decorator.getDelegate());
    }

    @Nested
    class WriteThroughOnSuccess {

        @Test
        void getDirectReportsDelegateSucceedsReturnsReports() {
            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);

            List<UUID> result = decorator.getDirectReports(ORG_ID, MANAGER_ID);

            assertEquals(REPORTS, result);
        }

        @Test
        void getDirectReportsCalledTwiceForwardsBothCallsToDelegate() {
            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);

            decorator.getDirectReports(ORG_ID, MANAGER_ID);
            decorator.getDirectReports(ORG_ID, MANAGER_ID);

            verify(delegate, times(2)).getDirectReports(ORG_ID, MANAGER_ID);
        }

        @Test
        void getDirectReportsWithNamesDelegatesAndPreservesDisplayNames() {
            when(delegate.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(REPORTS_WITH_NAMES);

            List<DirectReport> result = decorator.getDirectReportsWithNames(ORG_ID, MANAGER_ID);

            assertEquals(REPORTS_WITH_NAMES, result);
            verify(delegate, times(1)).getDirectReportsWithNames(ORG_ID, MANAGER_ID);
        }

        @Test
        void getOrgRosterDelegatesAndCachesRoster() {
            when(delegate.getOrgRoster(ORG_ID)).thenReturn(ROSTER);

            List<OrgRosterEntry> result = decorator.getOrgRoster(ORG_ID);

            assertEquals(ROSTER, result);
        }
    }

    @Nested
    class FallbackOnDelegateFailure {

        @Test
        void getDirectReportsDelegateFailsReturnsCachedReportsOnHit() {
            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);
            decorator.getDirectReports(ORG_ID, MANAGER_ID);

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("Upstream down"));

            List<UUID> result = decorator.getDirectReports(ORG_ID, MANAGER_ID);

            assertEquals(REPORTS, result);
        }

        @Test
        void getDirectReportsDelegateFailsRethrowsWhenCacheIsCold() {
            when(delegate.getDirectReports(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("Upstream down"));

            assertThrows(RuntimeException.class,
                    () -> decorator.getDirectReports(ORG_ID, MANAGER_ID));
        }

        @Test
        void getDirectReportsDifferentManagersHaveIsolatedCacheEntries() {
            UUID manager2 = UUID.randomUUID();
            List<UUID> reports2 = List.of(UUID.randomUUID());

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);
            when(delegate.getDirectReports(ORG_ID, manager2)).thenReturn(reports2);

            decorator.getDirectReports(ORG_ID, MANAGER_ID);
            decorator.getDirectReports(ORG_ID, manager2);

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("down"));
            when(delegate.getDirectReports(ORG_ID, manager2))
                    .thenThrow(new RuntimeException("down"));

            assertEquals(REPORTS, decorator.getDirectReports(ORG_ID, MANAGER_ID));
            assertEquals(reports2, decorator.getDirectReports(ORG_ID, manager2));
        }

        @Test
        void getDirectReportsWithNamesFallsBackToCachedNamedReports() {
            when(delegate.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(REPORTS_WITH_NAMES);
            decorator.getDirectReportsWithNames(ORG_ID, MANAGER_ID);

            when(delegate.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("Upstream down"));

            assertEquals(REPORTS_WITH_NAMES, decorator.getDirectReportsWithNames(ORG_ID, MANAGER_ID));
        }

        @Test
        void getOrgRosterFallsBackToCachedRoster() {
            when(delegate.getOrgRoster(ORG_ID)).thenReturn(ROSTER);
            decorator.getOrgRoster(ORG_ID);

            when(delegate.getOrgRoster(ORG_ID)).thenThrow(new RuntimeException("Upstream down"));

            assertEquals(ROSTER, decorator.getOrgRoster(ORG_ID));
        }

        @Test
        void getOrgTeamGroupsFallsBackToCachedRosterWhenGroupCallFails() {
            Map<UUID, OrgTeamGroup> teamGroups = new LinkedHashMap<>();
            teamGroups.put(MANAGER_ID, new OrgTeamGroup(MANAGER_ID, "Grace Hopper", List.of(ROSTER.get(1), ROSTER.get(2))));
            when(delegate.getOrgRoster(ORG_ID)).thenReturn(ROSTER);
            when(delegate.getOrgTeamGroups(ORG_ID)).thenReturn(teamGroups);

            decorator.getOrgRoster(ORG_ID);
            decorator.getOrgTeamGroups(ORG_ID);

            when(delegate.getOrgTeamGroups(ORG_ID)).thenThrow(new RuntimeException("Upstream down"));

            Map<UUID, OrgTeamGroup> fallback = decorator.getOrgTeamGroups(ORG_ID);
            assertEquals(1, fallback.size());
            assertEquals("Grace Hopper", fallback.get(MANAGER_ID).managerDisplayName());
            assertEquals(2, fallback.get(MANAGER_ID).members().size());
        }
    }

    @Nested
    class TtlExpiry {

        @Test
        void getDirectReportsRethrowsAfterCacheExpiry() {
            AtomicLong nanoTime = new AtomicLong(0L);
            Ticker ticker = nanoTime::get;

            Cache<String, List<UUID>> timedCache = Caffeine.newBuilder()
                    .maximumSize(CachingOrgGraphClientDecorator.MAX_SIZE)
                    .expireAfterWrite(
                            Duration.ofSeconds(CachingOrgGraphClientDecorator.TTL_SECONDS))
                    .ticker(ticker)
                    .build();

            CachingOrgGraphClientDecorator timedDecorator =
                    new CachingOrgGraphClientDecorator(delegate, timedCache);

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);
            timedDecorator.getDirectReports(ORG_ID, MANAGER_ID);

            nanoTime.set(Duration.ofSeconds(61L).toNanos());

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("Upstream down"));

            assertThrows(RuntimeException.class,
                    () -> timedDecorator.getDirectReports(ORG_ID, MANAGER_ID));
        }

        @Test
        void getDirectReportsReturnsCachedReportsWhenDelegateFailsWithinTtl() {
            AtomicLong nanoTime = new AtomicLong(0L);
            Ticker ticker = nanoTime::get;

            Cache<String, List<UUID>> timedCache = Caffeine.newBuilder()
                    .maximumSize(CachingOrgGraphClientDecorator.MAX_SIZE)
                    .expireAfterWrite(
                            Duration.ofSeconds(CachingOrgGraphClientDecorator.TTL_SECONDS))
                    .ticker(ticker)
                    .build();

            CachingOrgGraphClientDecorator timedDecorator =
                    new CachingOrgGraphClientDecorator(delegate, timedCache);

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(REPORTS);
            timedDecorator.getDirectReports(ORG_ID, MANAGER_ID);

            nanoTime.set(Duration.ofSeconds(59L).toNanos());

            when(delegate.getDirectReports(ORG_ID, MANAGER_ID))
                    .thenThrow(new RuntimeException("Upstream down"));

            List<UUID> result = timedDecorator.getDirectReports(ORG_ID, MANAGER_ID);
            assertEquals(REPORTS, result);
        }
    }
}
