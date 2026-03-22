package com.weekly.forecast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ForecastComputeJob}.
 */
class ForecastComputeJobTest {

    private TargetDateForecastService targetDateForecastService;
    private MeterRegistry meterRegistry;
    private Counter counter;
    private ForecastComputeJob job;

    @BeforeEach
    void setUp() {
        targetDateForecastService = mock(TargetDateForecastService.class);
        meterRegistry = mock(MeterRegistry.class);
        counter = mock(Counter.class);
        when(meterRegistry.counter(ForecastComputeJob.COUNTER_FORECAST_RECOMPUTE_TOTAL)).thenReturn(counter);
        job = new ForecastComputeJob(targetDateForecastService, meterRegistry);
    }

    @Test
    void recomputeAllProcessesEveryOrgAndIncrementsMetricPerSuccess() {
        UUID firstOrg = UUID.randomUUID();
        UUID secondOrg = UUID.randomUUID();
        when(targetDateForecastService.getForecastableOrgIds()).thenReturn(List.of(firstOrg, secondOrg));
        when(targetDateForecastService.recomputeForecastsForOrg(firstOrg)).thenReturn(List.of(new LatestForecastEntity(firstOrg, UUID.randomUUID())));
        when(targetDateForecastService.recomputeForecastsForOrg(secondOrg)).thenReturn(List.of(new LatestForecastEntity(secondOrg, UUID.randomUUID())));

        job.recomputeAll();

        verify(targetDateForecastService).recomputeForecastsForOrg(firstOrg);
        verify(targetDateForecastService).recomputeForecastsForOrg(secondOrg);
        verify(counter, times(2)).increment();
    }

    @Test
    void recomputeAllContinuesWhenOneOrgFails() {
        UUID failingOrg = UUID.randomUUID();
        UUID succeedingOrg = UUID.randomUUID();
        when(targetDateForecastService.getForecastableOrgIds()).thenReturn(List.of(failingOrg, succeedingOrg));
        doThrow(new RuntimeException("boom"))
                .when(targetDateForecastService).recomputeForecastsForOrg(failingOrg);
        when(targetDateForecastService.recomputeForecastsForOrg(succeedingOrg))
                .thenReturn(List.of(new LatestForecastEntity(succeedingOrg, UUID.randomUUID())));

        job.recomputeAll();

        verify(targetDateForecastService).recomputeForecastsForOrg(failingOrg);
        verify(targetDateForecastService).recomputeForecastsForOrg(succeedingOrg);
        verify(counter).increment();
    }

    @Test
    void recomputeAllDoesNothingWhenNoOrgsExist() {
        when(targetDateForecastService.getForecastableOrgIds()).thenReturn(List.of());

        job.recomputeAll();

        verify(targetDateForecastService, never()).recomputeForecastsForOrg(any());
    }
}
