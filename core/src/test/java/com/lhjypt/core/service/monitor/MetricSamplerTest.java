package com.lhjypt.core.service.monitor;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.MetricSampleMapper;
import com.lhjypt.core.model.monitor.MetricSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricSampler 定时采样与分布式锁集成测试。
 */
class MetricSamplerTest {

    @Test
    @DisplayName("采样经分布式锁执行，抢到锁后写入全部指标")
    void sampleRunsThroughLockAndRecordsMetrics() {
        MetricSampleMapper mapper = mock(MetricSampleMapper.class);
        DistributedLock lock = mock(DistributedLock.class);
        when(lock.execute(eq(MetricSampler.LOCK_KEY), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });
        MetricSampler sampler = new MetricSampler(mapper, lock);

        sampler.sample();

        verify(lock).execute(eq(MetricSampler.LOCK_KEY), any(Runnable.class));
        verify(mapper, times(5)).insert(any(MetricSample.class));
    }

    @Test
    @DisplayName("未抢到锁时不采样")
    void sampleSkippedWhenLockNotAcquired() {
        MetricSampleMapper mapper = mock(MetricSampleMapper.class);
        DistributedLock lock = mock(DistributedLock.class);
        when(lock.execute(anyString(), any(Runnable.class))).thenReturn(false);
        MetricSampler sampler = new MetricSampler(mapper, lock);

        sampler.sample();

        verify(mapper, never()).insert(any(MetricSample.class));
    }
}
