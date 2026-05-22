package com.azaricomm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotifWorkerMetrics {

    private final MeterRegistry registry;

    public NotifWorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordNotificationReceived() {
        Counter.builder("notifworker.notifications.received")
                .description("Notifications received from the queue")
                .register(registry)
                .increment();
    }

    public void recordNotificationDelivered(String provider) {
        Counter.builder("notifworker.notifications.delivered")
                .tag("provider", provider != null && !provider.isBlank() ? provider : "unknown")
                .description("Notifications successfully delivered to a provider")
                .register(registry)
                .increment();
    }

    public void recordNotificationFailed(String provider) {
        Counter.builder("notifworker.notifications.failed")
                .tag("provider", provider != null && !provider.isBlank() ? provider : "unknown")
                .description("Notifications that failed delivery")
                .register(registry)
                .increment();
    }

    public void recordNotificationDiscarded(String reason) {
        Counter.builder("notifworker.notifications.discarded")
                .tag("reason", reason != null ? reason : "unknown")
                .description("Notifications discarded without delivery attempt")
                .register(registry)
                .increment();
    }
}
