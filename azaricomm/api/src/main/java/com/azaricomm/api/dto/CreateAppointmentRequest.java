package com.azaricomm.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class CreateAppointmentRequest {

    @NotBlank(message = "OrganisationId is required")
    private String organizationId;

    @NotNull(message = "ScheduledTime is required")
    @Future(message = "ScheduledTime must be a future date and time")
    private Instant scheduledTime;

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

    // Getters and Setters
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

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
}