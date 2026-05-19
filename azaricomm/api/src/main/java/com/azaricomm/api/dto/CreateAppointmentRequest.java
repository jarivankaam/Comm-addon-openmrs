package com.azaricomm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateAppointmentRequest {

    @NotBlank(message = "OrganisationId is required")
    private String organizationId;

    @NotBlank(message = "ScheduledTime is required")
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$",
            message = "ScheduledTime must be a valid ISO 8601 string (e.g., 2026-05-20T10:00:00 or 2026-05-20T10:00:00Z)"
    )
    private String scheduledTime;

    @NotBlank(message = "PatientId is required")
    private String patientId;

    @NotBlank(message = "patientPhone is required")
    private String patientPhone;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Provider is required")
    private String provider;

    private String instructions;
    private String timezone;

    // Getters and Setters
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}