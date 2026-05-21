package com.azaricomm.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiMetricsTest {

    private MeterRegistry meterRegistry;
    private ApiMetrics apiMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        apiMetrics = new ApiMetrics(meterRegistry);
    }

    @Test
    void recordAppointmentReceived_ShouldIncrementCounter_WithCorrectTag() {
        apiMetrics.recordAppointmentReceived("org-test");

        Counter counter = meterRegistry.find("appointments.received").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        assertEquals("org-test", counter.getId().getTag("organization"));
    }

    @Test
    void recordAppointmentReceived_WhenOrganizationIdIsNull_ShouldFallbackToUnknown() {
        apiMetrics.recordAppointmentReceived(null);

        Counter counter = meterRegistry.find("appointments.received").counter();
        assertNotNull(counter);
        assertEquals("unknown", counter.getId().getTag("organization"));
    }

    @Test
    void recordAppointmentReceived_MultipleCalls_ShouldAccumulateCorrectly() {
        apiMetrics.recordAppointmentReceived("org-1");
        apiMetrics.recordAppointmentReceived("org-1");
        apiMetrics.recordAppointmentReceived("org-2");

        Counter counterOrg1 = meterRegistry.find("appointments.received").tag("organization", "org-1").counter();
        Counter counterOrg2 = meterRegistry.find("appointments.received").tag("organization", "org-2").counter();

        assertNotNull(counterOrg1);
        assertNotNull(counterOrg2);
        assertEquals(2.0, counterOrg1.count());
        assertEquals(1.0, counterOrg2.count());
    }

    @Test
    void recordAppointmentCancelled_ShouldIncrementCounter_WithCorrectTag() {
        apiMetrics.recordAppointmentCancelled("org-test-cancel");

        Counter counter = meterRegistry.find("appointments.cancelled").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        assertEquals("org-test-cancel", counter.getId().getTag("organization"));
    }

    @Test
    void recordAppointmentCancelled_WhenOrganizationIdIsNull_ShouldFallbackToUnknown() {
        apiMetrics.recordAppointmentCancelled(null);

        Counter counter = meterRegistry.find("appointments.cancelled").counter();
        assertNotNull(counter);
        assertEquals("unknown", counter.getId().getTag("organization"));
    }

    @Test
    void recordAppointmentReceived_WhenOrganizationIdIsBlank_ShouldFallbackToUnknown() {

        apiMetrics.recordAppointmentReceived("");
        apiMetrics.recordAppointmentReceived("   ");
        
        Counter counter = meterRegistry.find("appointments.received").tag("organization", "unknown").counter();

        assertNotNull(counter, "Lege of blanke organisatie ID's moeten worden omgezet naar 'unknown'");
        assertEquals(2.0, counter.count());
    }
}