package com.azaricomm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * Notification received from OpenMRS via RabbitMQ.
 * Mirrors the DTO published by the azaricomm OpenMRS module.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationMessage implements Serializable {

    private String organizationId;
    private String patientUuid;
    private String patientPhone;
    private String subject;
    private String body;
    private String appointmentUuid;
    private String appointmentDateTime;
    private String appointmentLocation;
    private String instructions;
    private String timezone;
    private String notificationType;
    private String provider; // which messaging provider to use: swiftsend, legacylink, asyncflow, securepost

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getAppointmentUuid() { return appointmentUuid; }
    public void setAppointmentUuid(String appointmentUuid) { this.appointmentUuid = appointmentUuid; }

    public String getAppointmentDateTime() { return appointmentDateTime; }
    public void setAppointmentDateTime(String appointmentDateTime) { this.appointmentDateTime = appointmentDateTime; }

    public String getAppointmentLocation() { return appointmentLocation; }
    public void setAppointmentLocation(String appointmentLocation) { this.appointmentLocation = appointmentLocation; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    @Override
    public String toString() {
        return "NotificationMessage{" +
                "org='" + organizationId + '\'' +
                ", patient='" + patientUuid + '\'' +
                ", phone='" + patientPhone + '\'' +
                ", type='" + notificationType + '\'' +
                ", provider='" + provider + '\'' +
                ", appointment='" + appointmentUuid + '\'' +
                '}';
    }
}
