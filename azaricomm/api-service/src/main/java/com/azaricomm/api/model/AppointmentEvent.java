package com.azaricomm.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "appointment_events")
public class AppointmentEvent {

    @Id
    private String id;

    private String appointmentId;
    private String status;
    private String fhirJson;
    private Instant receivedAt;

    public AppointmentEvent() {}

    public AppointmentEvent(String appointmentId, String status, String fhirJson) {
        this.appointmentId = appointmentId;
        this.status = status;
        this.fhirJson = fhirJson;
        this.receivedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAppointmentId() { return appointmentId; }
    public String getStatus() { return status; }
    public String getFhirJson() { return fhirJson; }
    public Instant getReceivedAt() { return receivedAt; }
}
