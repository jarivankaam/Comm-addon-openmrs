package com.azaricomm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SchedulerMetrics {

    private final MeterRegistry registry;

    public SchedulerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordNotificationQueued(String notificationType) {
        Counter.builder("scheduler.notifications.queued")
                .tag("type", notificationType != null ? notificationType : "unknown")
                .description("Notifications queued by the scheduler")
                .register(registry)
                .increment();
    }

    public void recordNotificationFailed(String notificationType) {
        Counter.builder("scheduler.notifications.failed")
                .tag("type", notificationType != null ? notificationType : "unknown")
                .description("Notifications that failed to be queued by the scheduler")
                .register(registry)
                .increment();
    }
}
