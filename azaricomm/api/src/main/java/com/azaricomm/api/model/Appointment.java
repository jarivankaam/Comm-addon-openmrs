package com.azaricomm.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "appointments")
public class Appointment {

    @Id
    private String id;

    private String organizationId;
    private String patientId;
    private String patientPhone;
    private String subject;
    private String body;
    private String appointmentDateTime; // ISO 8601 string, e.g. "2026-05-20T10:00:00"
    private String appointmentLocation;
    private String instructions;
    private String timezone;
    private String provider; // swiftsend, legacylink, asyncflow, securepost

    private String status; // SCHEDULED, QUEUED, SENT, CANCELLED, FAILED
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getAppointmentDateTime() { return appointmentDateTime; }
    public void setAppointmentDateTime(String appointmentDateTime) { this.appointmentDateTime = appointmentDateTime; }

    public String getAppointmentLocation() { return appointmentLocation; }
    public void setAppointmentLocation(String appointmentLocation) { this.appointmentLocation = appointmentLocation; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
