package com.weekly.config;

import com.weekly.auth.CachingOrgGraphClientDecorator;
import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.auth.OrgGraphClient;
import com.weekly.rcdo.CachingRcdoClientDecorator;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registers Caffeine-backed fallback decorators for {@link RcdoClient} and
 * {@link OrgGraphClient} as {@code @Primary} beans (PRD §9.2).
 *
 * <p>The decorators wrap the existing in-memory implementations and add an
 * in-process LRU cache (maxSize=100, TTL=60 s) as a fallback when the delegate
 * throws (e.g. Redis or upstream service is down). The underlying concrete
 * implementations ({@link InMemoryRcdoClient}, {@link InMemoryOrgGraphClient})
 * remain available for injection by concrete type (used by initializers and
 * dev/test helpers).
 */
@Configuration
public class CacheConfiguration {

    /**
     * Primary {@link RcdoClient} bean: a Caffeine-backed decorator around
     * {@link InMemoryRcdoClient}.  When the delegate throws, the decorator
     * serves the last-known tree from the Caffeine LRU cache and increments
     * the {@code rcdo_cache_fallback_active} gauge metric.
     */
    @Bean
    @Primary
    public RcdoClient cachingRcdoClient(
            InMemoryRcdoClient inMemoryRcdoClient,
            MeterRegistry meterRegistry) {
        return new CachingRcdoClientDecorator(inMemoryRcdoClient, meterRegistry);
    }

    /**
     * Primary {@link OrgGraphClient} bean: a Caffeine-backed decorator around
     * {@link InMemoryOrgGraphClient}.  When the delegate throws, the decorator
     * serves the last-known direct-report list from the Caffeine LRU cache.
     */
    @Bean
    @Primary
    public OrgGraphClient cachingOrgGraphClient(
            InMemoryOrgGraphClient inMemoryOrgGraphClient) {
        return new CachingOrgGraphClientDecorator(inMemoryOrgGraphClient);
    }
}
