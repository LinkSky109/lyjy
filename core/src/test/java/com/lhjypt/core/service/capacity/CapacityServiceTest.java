package com.lhjypt.core.service.capacity;

import com.lhjypt.core.config.CapacityProperties;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.CapacitySnapshotMapper;
import com.lhjypt.core.model.capacity.CapacitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapacityServiceTest {

    private CapacitySnapshotMapper snapshotMapper;
    private CapacityMetricsProvider metricsProvider;
    private AutoScaler autoScaler;
    private CapacityProperties properties;
    private CapacityService service;

    @BeforeEach
    void setUp() {
        snapshotMapper = mock(CapacitySnapshotMapper.class);
        metricsProvider = mock(CapacityMetricsProvider.class);
        autoScaler = mock(AutoScaler.class);
        properties = new CapacityProperties();
        properties.setWarnPct(0.8);
        properties.setScalePct(0.9);
        properties.setAutoscaleEnabled(true);
        properties.setDesiredMin(1);
        properties.setDesiredMax(10);
        service = new CapacityService(snapshotMapper, properties, metricsProvider,
                autoScaler, mock(DistributedLock.class));
    }

    @Test
    @DisplayName("水位 ≥90% → SCALE 并触发扩容")
    void scaleStatusTriggersAutoscaler() {
        when(metricsProvider.sample()).thenReturn(new CapacityMetrics(
                95d, 30d, 40d, 0, 0, 0));

        CapacitySnapshot snapshot = service.collect();

        assertThat(snapshot.getStatus()).isEqualTo(CapacitySnapshot.SCALE);
        assertThat(snapshot.getDesiredReplicas()).isEqualTo(10);
        assertThat(snapshot.getLoadPct().doubleValue()).isCloseTo(95d, within(0.01d));
        verify(snapshotMapper).insert(any(CapacitySnapshot.class));
        verify(autoScaler).scaleTo(10);
    }

    @Test
    @DisplayName("水位 85% → WARN（不扩容）")
    void warnStatusNoScale() {
        when(metricsProvider.sample()).thenReturn(new CapacityMetrics(
                85d, 40d, 40d, 0, 0, 0));

        CapacitySnapshot snapshot = service.collect();

        assertThat(snapshot.getStatus()).isEqualTo(CapacitySnapshot.WARN);
        assertThat(snapshot.getDesiredReplicas()).isEqualTo(1);
        verify(autoScaler, never()).scaleTo(anyInt());
    }

    @Test
    @DisplayName("水位 60% → NORMAL")
    void normalStatus() {
        when(metricsProvider.sample()).thenReturn(new CapacityMetrics(
                60d, 55d, 50d, 0, 0, 0));

        CapacitySnapshot snapshot = service.collect();

        assertThat(snapshot.getStatus()).isEqualTo(CapacitySnapshot.NORMAL);
        verify(autoScaler, never()).scaleTo(anyInt());
    }

    @Test
    @DisplayName("关闭自动伸缩后 SCALE 仅记录不触发")
    void autoscaleDisabledSkipsTrigger() {
        properties.setAutoscaleEnabled(false);
        when(metricsProvider.sample()).thenReturn(new CapacityMetrics(
                99d, 99d, 99d, 0, 0, 0));

        CapacitySnapshot snapshot = service.collect();

        assertThat(snapshot.getStatus()).isEqualTo(CapacitySnapshot.SCALE);
        verify(autoScaler, never()).scaleTo(anyInt());
        ArgumentCaptor<CapacitySnapshot> captor =
                ArgumentCaptor.forClass(CapacitySnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertThat(captor.getValue().getDetail()).contains("desiredReplicas");
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}