package com.azaricomm.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ApiMetrics {

    private final MeterRegistry registry;

    public ApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAppointmentReceived(String organizationId) {
        Counter.builder("appointments.received")
                .tag("organization", organizationId != null ? organizationId : "unknown")
                .description("Appointments received from OpenMRS")
                .register(registry)
                .increment();
    }

    public void recordAppointmentCancelled(String organizationId) {
        Counter.builder("appointments.cancelled")
                .tag("organization", organizationId != null ? organizationId : "unknown")
                .description("Appointments cancelled")
                .register(registry)
                .increment();
    }
}