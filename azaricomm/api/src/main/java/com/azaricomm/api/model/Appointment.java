package com.azaricomm.api.model;

import com.azaricomm.api.controller.AppointmentController;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Document(collection = "appointments")
public class Appointment {
    private static final Logger log = LoggerFactory.getLogger(Appointment.class);
    @Id
    private String id;

    @NotBlank(message = "OrganisationId is required")
    private String organizationId;
    @NotBlank(message = "ScheduledTime is required")
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$",
            message = "ScheduledTime must be a valid ISO 8601 string (e.g., 2026-05-20T10:00:00 or 2026-05-20T10:00:00Z)"
    )
    private String scheduledTime; // ISO 8601 string, e.g. "2026-05-20T10:00:00"

    @NotBlank(message = "PatientId is required")
    private String patientId;
    @NotBlank(message = "patientPhone is required")
    private String patientPhone;
    @NotBlank(message = "Subject is required")
    private String subject;
    @NotBlank(message = "Location is required")
    private String location;
    private String instructions;
    @NotBlank(message = "Provider is required")
    private String provider; // swiftsend, legacylink, asyncflow, securepost

    private String timezone;
    @NotNull(message = "Status can only be: SCHEDULED, QUEUED, SENT, CANCELLED, or FAILED")
    private AppointmentStatus status; // SCHEDULED, QUEUED, SENT, CANCELLED, FAILED
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

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(String status) {
        if (status != null) {
            try {
                log.info("status is:" + status);
                this.status = AppointmentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.status = null; // This triggers @NotNull
            }
        }
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
