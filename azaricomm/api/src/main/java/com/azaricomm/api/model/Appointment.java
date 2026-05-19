package com.azaricomm.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Document(collection = "appointments")
public class Appointment {

    @Id
    private String id;

    private String organizationId;
    private Instant scheduledTime;
    private String patientId;
    private String patientPhone;
    private String subject;
    private String location;
    private String instructions;
    private String provider; // swiftsend, legacylink, asyncflow, securepost
    private String timezone;

    @NotNull(message = "Status can only be: SCHEDULED, QUEUED, SENT, CANCELLED, or FAILED")
    private AppointmentStatus status; // SCHEDULED, QUEUED, SENT, CANCELLED, FAILED
    private Instant createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expireAt;

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

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public AppointmentStatus getStatus() { return status; }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    // In case it isn't send as an enum.
    public void setStatus(String status) {
        if (status != null) {
            try {
                this.status = AppointmentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.status = null;
            }
        }
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpireAt() {return expireAt;}

    public void setExpireAt(Instant expireAt) {this.expireAt = expireAt;}
}