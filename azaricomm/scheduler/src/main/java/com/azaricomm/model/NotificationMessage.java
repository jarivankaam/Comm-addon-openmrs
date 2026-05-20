package com.azaricomm.model;

import java.io.Serializable;

public class NotificationMessage implements Serializable {

    private String organizationId;
    private String patientId;
    private String patientPhone;
    private String subject;
    private String body;
    private String appointmentId;
    private String appointmentDateTime;
    private String appointmentLocation;
    private String instructions;
    private String timezone;
    private String notificationType;
    private String provider;

    public NotificationMessage() {}

    public NotificationMessage(String appointmentId, String notificationType, String provider) {
        this.appointmentId = appointmentId;
        this.notificationType = notificationType;
        this.provider = provider;
    }

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

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

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
}
